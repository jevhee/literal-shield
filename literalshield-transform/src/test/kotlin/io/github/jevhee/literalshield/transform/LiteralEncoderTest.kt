package io.github.jevhee.literalshield.transform

import io.github.jevhee.literalshield.runtime.LiteralDecoder
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class LiteralEncoderTest {
    @Test fun independentGoldenVectors() {
        val expected = listOf("d5dc2d695038f7ca9267", "aecd5a2d1470fdca929d")
        for (variant in 0..1) {
            val payload = LiteralEncoder.encode("hello 👋", 123, variant).payload
            assertEquals(expected[variant], payload.joinToString("") { "%02x".format(it) })
        }
    }

    @Test fun roundTripAndIdentity() {
        val corpus = listOf("", "hello", "https://api.example.test/v1", "\u0000", "a\nb\r\nc", "日本語 café 👋", "x".repeat(2048))
        for (value in corpus) for (seed in listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) for (variant in 0..1) {
            val encoded = LiteralEncoder.encode(value, seed, variant)
            assertSame(value.intern(), LiteralDecoder.decode(encoded.payload, seed, variant, 1))
        }
    }

    @Test fun randomUnicodeCorpus() {
        val random = Random(20260907)
        repeat(1000) {
            val value = buildString { repeat(random.nextInt(128)) {
                val point = random.nextInt(0x110000)
                if (point !in 0xD800..0xDFFF) appendCodePoint(point)
            } }
            val seed = random.nextLong()
            for (variant in 0..1) {
                val encoded = LiteralEncoder.encode(value, seed, variant)
                assertEquals(value, LiteralDecoder.decode(encoded.payload, seed, variant, 1))
            }
        }
    }

    @Test fun determinismAndDiversification() {
        val value = "https://api.example.test/production"
        val a = LiteralEncoder.encode(value, 123, 0)
        assertArrayEquals(a.payload, LiteralEncoder.encode(value, 123, 0).payload)
        assertFalse(a.payload.contentEquals(LiteralEncoder.encode(value, 124, 0).payload))
        assertFalse(a.payload.contentEquals(LiteralEncoder.encode(value, 123, 1).payload))
        assertNotEquals(LiteralEncoder.seed("build", "a"), LiteralEncoder.seed("build", "b"))
        assertNotEquals(LiteralEncoder.seed("ab", "c"), LiteralEncoder.seed("a", "bc"))
    }

    @Test fun rejectsUnsupportedData() {
        for (value in listOf("\uD800", "\uDC00", "\uD800x")) {
            assertFalse(LiteralEncoder.validUtf16(value))
            assertThrows(IllegalArgumentException::class.java) { LiteralEncoder.encode(value, 0, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { LiteralDecoder.decode(byteArrayOf(), 0, 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { LiteralDecoder.decode(byteArrayOf(), 0, 2, 1) }
    }
}
