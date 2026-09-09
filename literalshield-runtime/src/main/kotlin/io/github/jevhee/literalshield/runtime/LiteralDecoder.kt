package io.github.jevhee.literalshield.runtime

import java.nio.charset.StandardCharsets

/** Format v1 decoder. This is reversible obfuscation, not secret storage. */
object LiteralDecoder {
    @JvmStatic
    fun decode(payload: ByteArray, seed: Long, variant: Int, format: Int): String {
        require(format == 1 && variant in 0..1) { "LiteralShield: unsupported payload format" }
        val permutation = permutation(payload.size, seed)
        val decoded = ByteArray(payload.size)
        val xor = Stream(seed xor 0x584F525F5354524DL)
        val rotate = Stream(seed xor 0x524F545F5354524DL)
        decoded.indices.forEach { index ->
            var value = payload[permutation[index]].toInt() and 255
            if (variant == 1) {
                val distance = (rotate.next() and 7).toInt()
                value = ((value ushr distance) or (value shl (8 - distance))) and 255
            }
            decoded[index] = (value xor xor.next().toInt()).toByte()
        }
        return String(decoded, StandardCharsets.UTF_8).intern()
    }

    private fun permutation(length: Int, seed: Long): IntArray {
        val result = IntArray(length) { it }
        val stream = Stream(seed xor 0x5045524D5354524DL)
        for (index in length - 1 downTo 1) {
            val target = ((stream.next() ushr 1) % (index + 1)).toInt()
            val old = result[index]
            result[index] = result[target]
            result[target] = old
        }
        return result
    }

    private class Stream(seed: Long) {
        private var state = seed

        fun next(): Long {
            var value = state + 0x9E3779B97F4A7C15UL.toLong()
            state = value
            value = (value xor (value ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
            value = (value xor (value ushr 27)) * 0x94D049BB133111EBUL.toLong()
            return value xor (value ushr 31)
        }
    }
}
