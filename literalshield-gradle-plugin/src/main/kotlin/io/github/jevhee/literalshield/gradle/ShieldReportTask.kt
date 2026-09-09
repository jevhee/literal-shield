package io.github.jevhee.literalshield.gradle

import io.github.jevhee.literalshield.transform.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import java.util.zip.ZipFile

@CacheableTask
abstract class ShieldReportTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val jars: ListProperty<RegularFile>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val directories: ListProperty<Directory>
    @get:Input abstract val module: Property<String>
    @get:Input abstract val variant: Property<String>
    @get:Input abstract val scanMode: Property<String>
    @get:Input abstract val minimumLength: Property<Int>
    @get:Input abstract val excludePackages: ListProperty<String>
    @get:Input abstract val excludeClasses: ListProperty<String>
    @get:Input abstract val ignoredValues: SetProperty<String>
    @get:Input abstract val includePatterns: ListProperty<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun scan() {
        check(scanMode.get() == "REPORT") { "LiteralShield: set mode to REPORT for a source scan; instrumented classes are not a scan baseline" }
        val transformer = LiteralTransformer(ShieldConfig(minimumLength.get(), excludePackages.get(),
            excludeClasses.get(), ignoredValues.get(), includePatterns.get()))
        val records = mutableListOf<LiteralRecord>()
        fun inspect(bytes: ByteArray) { records += transformer.transform(bytes, module.get(), variant.get(), "report", false).records }
        directories.get().forEach { dir -> dir.asFile.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { inspect(it.readBytes()) } }
        jars.get().forEach { jar -> ZipFile(jar.asFile).use { zip ->
            zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry -> zip.getInputStream(entry).use { inspect(it.readBytes()) } }
        } }
        val eligible = records.count { it.decision == "ELIGIBLE" }
        val rows = records.sortedBy { it.id }.joinToString(",\n") {
            """{"id":${json(it.id)},"class":${json(it.className)},"member":${json(it.member)},"ordinal":${it.ordinal},"length":${it.length},"risk":${json(it.risk)},"decision":${json(it.decision)},"reason":${json(it.reason)}}"""
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("""{"schemaVersion":1,"mode":"REPORT","module":${json(module.get())},"variant":${json(variant.get())},"scanned":${records.size},"eligible":$eligible,"transformed":0,"skipped":${records.size - eligible},"records":[$rows]}""")
        logger.lifecycle("LiteralShield: {} scanned, {} eligible, 0 transformed", records.size, eligible)
    }

    private fun json(value: String): String = "\"" + value.map { c ->
        when (c) { '"' -> "\\\""; '\\' -> "\\\\"; else -> if (c.code < 32) "\\u%04x".format(c.code) else c.toString() }
    }.joinToString("") + "\""
}
