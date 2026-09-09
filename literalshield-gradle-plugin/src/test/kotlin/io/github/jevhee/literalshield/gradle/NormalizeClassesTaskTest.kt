package io.github.jevhee.literalshield.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.*

class NormalizeClassesTaskTest {
    private fun archive(name: String, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry(name)); it.write(bytes); it.closeEntry() }
        return out.toByteArray()
    }
    @Test fun removesUnusedPoolEntriesFromNestedClassJar() {
        val dir = Files.createTempDirectory("shield-normalize-test").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(dir).build()
            val writer = ClassWriter(0)
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Unused", null, "java/lang/Object", null)
            writer.visitAnnotation("Lio/github/jevhee/literalshield/runtime/Transformed;", false).visitEnd()
            writer.newUTF8("synthetic-unused-sentinel")
            writer.visitEnd()
            val originalJar = archive("fixture/Unused.class", writer.toByteArray())
            val inputBuffer = ByteArrayOutputStream()
            ZipOutputStream(inputBuffer).use {
                for (name in listOf("classes.jar", "libs/vendor.jar")) {
                    it.putNextEntry(ZipEntry(name)); it.write(originalJar); it.closeEntry()
                }
            }
            val input = inputBuffer.toByteArray()
            dir.resolve("input.aar").writeBytes(input)
            val task = project.tasks.register("normalize", NormalizeClassesTask::class.java).get()
            task.inputArchive.set(project.layout.projectDirectory.file("input.aar"))
            task.outputArchive.set(project.layout.projectDirectory.file("output.aar"))
            task.normalize()
            ZipFile(dir.resolve("output.aar")).use { aar ->
                assertArrayEquals(originalJar, aar.getInputStream(aar.getEntry("libs/vendor.jar")).use { it.readBytes() })
                ZipInputStream(aar.getInputStream(aar.getEntry("classes.jar"))).use { jar ->
                    assertEquals("fixture/Unused.class", jar.nextEntry.name)
                    val result = jar.readBytes()
                    assertFalse(String(result, Charsets.ISO_8859_1).contains("synthetic-unused-sentinel"))
                    assertEquals("fixture/Unused", ClassReader(result).className)
                }
            }
            assertArrayEquals(input, dir.resolve("input.aar").readBytes())
        } finally { dir.deleteRecursively() }
    }
}
