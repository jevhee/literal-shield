package io.github.jevhee.literalshield.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import io.github.jevhee.literalshield.transform.LiteralTransformer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Runs after AAR packaging: AGP instrumentation can retain unused original pool entries. */
@CacheableTask
abstract class NormalizeClassesTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val inputArchive: RegularFileProperty
    @get:OutputFile abstract val outputArchive: RegularFileProperty

    @TaskAction fun normalize() {
        val output = outputArchive.get().asFile
        output.parentFile.mkdirs()
        output.writeBytes(normalizeArchive(inputArchive.get().asFile.readBytes(), false))
    }

    private fun marked(bytes: ByteArray): Boolean {
        var marked = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                if (descriptor == LiteralTransformer.MARKER) marked = true
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return marked
    }

    private fun normalizeArchive(bytes: ByteArray, classJar: Boolean): ByteArray {
        val entries = sortedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                require(!entry.name.startsWith('/') && entry.name.split('/').none { it == ".." }) { "LiteralShield: unsafe archive entry" }
                val input = zip.readBytes()
                val content = when {
                    !classJar && entry.name == "classes.jar" -> normalizeArchive(input, true)
                    classJar && entry.name.endsWith(".class") && marked(input) -> {
                        val writer = ClassWriter(0)
                        ClassReader(input).accept(writer, 0)
                        writer.toByteArray()
                    }
                    else -> input
                }
                check(entries.put(entry.name, content) == null) { "LiteralShield: duplicate archive entry" }
            }
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0L })
            zip.write(content); zip.closeEntry()
        } }
        return output.toByteArray()
    }
}
