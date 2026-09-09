package io.github.jevhee.literalshield.transform

import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.*
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

class LiteralTransformerTest {
    private val sentinel = "https://sentinel.example.test/java-production"
    private fun fixture(privateField: Boolean = false, unsafe: Boolean = false): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Sample", null, "java/lang/Object", null)
        writer.visitField((if (privateField) Opcodes.ACC_PRIVATE else Opcodes.ACC_PUBLIC) or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            "URL", "Ljava/lang/String;", null, sentinel).visitEnd()
        val method = writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "value", "()Ljava/lang/String;", null, null)
        method.visitCode()
        if (unsafe) {
            method.visitLdcInsn("java.lang.String")
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false)
            method.visitInsn(Opcodes.POP)
        }
        method.visitLdcInsn(sentinel)
        method.visitInsn(Opcodes.ARETURN)
        method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd()
        return writer.toByteArray()
    }
    @Test fun transformsAndExecutesPrivateConstant() {
        val result = LiteralTransformer().transform(fixture(privateField = true), ":fixture", "release", "fixed")
        assertEquals(2, result.records.count { it.decision == "TRANSFORMED" })
        assertFalse(String(result.bytes, Charsets.ISO_8859_1).contains(sentinel))
        val diagnostics = StringWriter()
        CheckClassAdapter.verify(ClassReader(result.bytes), false, PrintWriter(diagnostics))
        assertEquals("", diagnostics.toString())
        val loaded = object : ClassLoader(javaClass.classLoader) {
            fun load() = defineClass("fixture.Sample", result.bytes, 0, result.bytes.size)
        }.load()
        assertSame(sentinel.intern(), loaded.getMethod("value").invoke(null))
        val field = loaded.getDeclaredField("URL").apply { isAccessible = true }
        assertSame(sentinel.intern(), field.get(null))
        val again = LiteralTransformer().transform(result.bytes, ":fixture", "release", "another")
        assertArrayEquals(result.bytes, again.bytes)
    }
    @Test fun preservesExportedConstantAndReportsIt() {
        val result = LiteralTransformer().transform(fixture(), ":fixture", "release", "fixed")
        assertEquals(1, result.records.count { it.reason == "EXPORTED_CONSTANT" })
        assertTrue(String(result.bytes, Charsets.ISO_8859_1).contains(sentinel))
        assertEquals(1, result.records.count { it.decision == "TRANSFORMED" })
    }
    @Test fun reportModeDoesNotMutateAndUnsafeMethodIsSkipped() {
        val original = fixture(unsafe = true)
        val result = LiteralTransformer().transform(original, ":fixture", "release", "fixed")
        assertArrayEquals(original, result.bytes)
        assertTrue(result.records.any { it.reason == "UNSAFE_CONTEXT" })
        val scan = LiteralTransformer().transform(fixture(), ":fixture", "release", "fixed", false)
        assertArrayEquals(fixture(), scan.bytes)
        assertTrue(scan.records.any { it.decision == "ELIGIBLE" })
        assertFalse(scan.records.toString().contains(sentinel))
    }
    @Test fun kotlinClassAlsoTransforms() {
        val bytes = javaClass.classLoader.getResourceAsStream("io/github/jevhee/literalshield/transform/KotlinFixture.class")!!.readBytes()
        val result = LiteralTransformer().transform(bytes, ":fixture", "release", "fixed")
        assertTrue(result.records.any { it.decision == "TRANSFORMED" })
        val loaded = object : ClassLoader(javaClass.classLoader) {
            fun load() = defineClass(KotlinFixture::class.java.name, result.bytes, 0, result.bytes.size)
        }.load()
        assertEquals("/v1/kotlin-production", loaded.getMethod("endpoint").invoke(loaded.getConstructor().newInstance()))
    }
}
class KotlinFixture { fun endpoint() = "/v1/kotlin-production" }
