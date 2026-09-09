package io.github.jevhee.literalshield.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.*
import java.nio.file.Files

class ShieldReportTaskTest {
    @Test fun defaultsAreReleaseOnlyAndDoNotRequireSeed() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("literalShield", LiteralShieldExtension::class.java)
        assertTrue(extension.enabled.get())
        assertEquals(setOf("release"), extension.buildTypes.get())
        assertEquals("PROTECT", extension.mode.get())
        assertFalse(extension.buildSeed.isPresent)
    }

    @Test fun scanDoesNotLeakPlaintextOrClaimTransforms() {
        val dir = Files.createTempDirectory("shield-report-test").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(dir).build()
            val input = dir.resolve("classes").apply { mkdirs() }
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Report", null, "java/lang/Object", null)
            val method = writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "value", "()Ljava/lang/String;", null, null)
            method.visitCode(); method.visitLdcInsn("/v1/synthetic-report-sentinel")
            method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd()
            val bytes = writer.toByteArray()
            input.resolve("Report.class").writeBytes(bytes)
            val task = project.tasks.register("scan", ShieldReportTask::class.java).get()
            task.module.set(":fixture"); task.variant.set("release"); task.scanMode.set("REPORT")
            task.minimumLength.set(4); task.excludePackages.set(emptyList()); task.excludeClasses.set(emptyList())
            task.ignoredValues.set(emptySet()); task.includePatterns.set(emptyList()); task.jars.set(emptyList())
            task.directories.set(listOf(project.layout.projectDirectory.dir("classes")))
            task.outputFile.set(project.layout.projectDirectory.file("scan.json"))
            task.scan()
            val report = dir.resolve("scan.json").readText()
            assertTrue(report.contains("\"eligible\":1"))
            assertTrue(report.contains("\"transformed\":0"))
            assertFalse(report.contains("synthetic-report-sentinel"))
            assertArrayEquals(bytes, input.resolve("Report.class").readBytes())
            task.scanMode.set("PROTECT")
            assertThrows(IllegalStateException::class.java) { task.scan() }
        } finally { dir.deleteRecursively() }
    }
}
