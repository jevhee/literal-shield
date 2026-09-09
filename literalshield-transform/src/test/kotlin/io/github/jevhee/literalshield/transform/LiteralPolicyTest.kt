package io.github.jevhee.literalshield.transform

import org.junit.Assert.*
import org.junit.Test

class LiteralPolicyTest {
    @Test fun classifierAndEscapeHatches() {
        val policy = LiteralPolicy(ShieldConfig(ignoredValues = setOf("api-production"), includePatterns = listOf("custom:.*")))
        assertTrue(policy.assess("https://example.test/api").eligible)
        assertEquals("MEDIUM", policy.assess("/v1/login").risk)
        assertEquals("HIGH", policy.assess("11111111-2222-3333-4444-555555555555").risk)
        assertTrue(policy.assess("custom:thing").eligible)
        assertEquals("IGNORED_VALUE", policy.assess("api-production").reason)
        assertFalse(policy.assess("success").eligible)
        assertEquals("SHORT_STRING", policy.assess("a").reason)
        assertEquals("UNSUPPORTED_UTF16", policy.assess("api-\uD800").reason)
    }
    @Test fun exclusionsRespectBoundariesAndSyntheticCode() {
        val policy = LiteralPolicy(ShieldConfig(excludePackages = listOf("com.example.generated"), excludeClasses = listOf("**/Skip*")))
        assertTrue(policy.excludedClass("com/example/generated/Thing"))
        assertFalse(policy.excludedClass("com/example/generatedish/Thing"))
        assertTrue(policy.excludedClass("com/example/SkipMe"))
        assertTrue(policy.excludedClass("com/example/R\$string"))
        assertFalse(policy.excludedClass("com/example/Main\$lambda1"))
    }
    @Test fun invalidRegexDoesNotEchoPattern() {
        val error = assertThrows(IllegalArgumentException::class.java) { ShieldConfig(includePatterns = listOf("secret[")) }
        assertFalse(error.message!!.contains("secret"))
    }
}
