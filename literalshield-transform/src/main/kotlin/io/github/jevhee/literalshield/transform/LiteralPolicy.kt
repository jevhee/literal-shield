package io.github.jevhee.literalshield.transform

import java.net.URI

data class ShieldConfig(
    val minimumLength: Int = 4,
    val excludePackages: List<String> = emptyList(),
    val excludeClasses: List<String> = emptyList(),
    val ignoredValues: Set<String> = emptySet(),
    val includePatterns: List<String> = emptyList(),
) {
    init {
        require(minimumLength >= 0) { "LiteralShield: minimumLength must be non-negative" }
        includePatterns.forEachIndexed { i, pattern ->
            try { Regex(pattern) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("LiteralShield: invalid include pattern at index $i")
            }
        }
    }
}

data class Assessment(val risk: String, val reason: String, val eligible: Boolean)

class LiteralPolicy(private val config: ShieldConfig) {
    private val includes = config.includePatterns.map(::Regex)
    private val excludes = config.excludeClasses.map { glob(it) }

    fun excludedClass(name: String): Boolean {
        val simple = name.substringAfterLast('/')
        return name.startsWith("io/github/jevhee/literalshield/runtime/") ||
            simple == "BuildConfig" || simple == "R" || simple.startsWith("R$") ||
            config.excludePackages.any {
                val prefix = it.replace('.', '/').trimEnd('/')
                name == prefix || name.startsWith("$prefix/")
            } || excludes.any { it.matches(name) }
    }

    fun assess(value: String): Assessment {
        fun skip(reason: String) = Assessment("LOW", reason, false)
        if (value in config.ignoredValues) return skip("IGNORED_VALUE")
        if (value.length < config.minimumLength) return skip("SHORT_STRING")
        if (!LiteralEncoder.validUtf16(value)) return skip("UNSUPPORTED_UTF16")
        if (UUID.matches(value)) return Assessment("HIGH", "UUID", true)
        val categories = listOf(value.any(Char::isUpperCase), value.any(Char::isLowerCase),
            value.any(Char::isDigit), value.any { !it.isLetterOrDigit() }).count { it }
        if (value.length >= 24 && value.all { it.code in 33..126 } && categories >= 3)
            return Assessment("HIGH", "TOKEN_LIKE", true)
        val url = try { URI(value).let { it.scheme in listOf("http", "https") && !it.host.isNullOrEmpty() } }
        catch (_: Exception) { false }
        if (url) return Assessment("MEDIUM", "URL", true)
        if (PATH.matches(value)) return Assessment("MEDIUM", "ENDPOINT", true)
        if (value.split(Regex("[^A-Za-z0-9]+")).any { it.lowercase() in TOKENS })
            return Assessment("MEDIUM", "CONFIG_IDENTIFIER", true)
        if (includes.any { it.matches(value) }) return Assessment("MEDIUM", "INCLUDE_PATTERN", true)
        return skip("NOT_CANDIDATE")
    }

    private fun glob(value: String): Regex {
        val result = StringBuilder("^")
        var i = 0
        while (i < value.length) {
            if (value[i] == '*') {
                if (i + 1 < value.length && value[i + 1] == '*') { result.append(".*"); i++ }
                else result.append("[^/]*")
            } else result.append(Regex.escape(value[i].toString()))
            i++
        }
        return Regex(result.append('$').toString())
    }

    companion object {
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val PATH = Regex("/[A-Za-z0-9._~-]+(?:/[A-Za-z0-9._~-]+)*")
        private val TOKENS = setOf("client", "env", "production", "staging", "api")
    }
}
