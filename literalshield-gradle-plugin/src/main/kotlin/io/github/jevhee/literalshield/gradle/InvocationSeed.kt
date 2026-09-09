package io.github.jevhee.literalshield.gradle

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.security.SecureRandom

/** Evaluated once per provider. Gradle rechecks ValueSource inputs on cache reuse. */
abstract class InvocationSeed : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = java.lang.Long.toUnsignedString(SecureRandom().nextLong(), 16)
}
