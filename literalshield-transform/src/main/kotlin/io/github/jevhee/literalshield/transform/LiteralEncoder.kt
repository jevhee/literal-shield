package io.github.jevhee.literalshield.transform

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Immutable-by-convention build-time payload; never written to reports. */
data class EncodedLiteral(val payload: ByteArray, val seed: Long, val variant: Int, val format: Int = 1)

object LiteralEncoder {
    fun validUtf16(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char.isHighSurrogate()) {
                if (index == value.length || !value[index++].isLowSurrogate()) return false
            } else if (char.isLowSurrogate()) return false
        }
        return true
    }

    fun seed(buildSeed: String, occurrence: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf("literalshield-v1", buildSeed, occurrence)) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return ByteBuffer.wrap(digest.digest()).long
    }

    fun encode(value: String, seed: Long, variant: Int): EncodedLiteral {
        require(validUtf16(value)) { "LiteralShield: unsupported UTF-16" }
        require(variant in 0..1) { "LiteralShield: unsupported decoder variant" }
        val bytes = value.toByteArray(Charsets.UTF_8)
        val permutation = IntArray(bytes.size) { it }
        val shuffle = Stream(seed xor 0x5045524D5354524DL)
        for (i in bytes.lastIndex downTo 1) {
            val j = ((shuffle.next() ushr 1) % (i + 1)).toInt()
            val old = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = old
        }
        val xor = Stream(seed xor 0x584F525F5354524DL)
        val rotate = Stream(seed xor 0x524F545F5354524DL)
        val payload = ByteArray(bytes.size)
        for (i in bytes.indices) {
            var valueByte = (bytes[i].toInt() xor xor.next().toInt()) and 255
            if (variant == 1) {
                val distance = (rotate.next() and 7).toInt()
                valueByte = ((valueByte shl distance) or (valueByte ushr (8 - distance))) and 255
            }
            payload[permutation[i]] = valueByte.toByte()
        }
        return EncodedLiteral(payload, seed, variant)
    }

    private class Stream(var state: Long) {
        fun next(): Long {
            state += 0x9E3779B97F4A7C15UL.toLong()
            var z = state
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
            return z xor (z ushr 31)
        }
    }
}
