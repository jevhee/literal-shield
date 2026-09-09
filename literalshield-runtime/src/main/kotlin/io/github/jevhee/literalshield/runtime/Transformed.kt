package io.github.jevhee.literalshield.runtime

/** Internal idempotence marker. Does not contain original literals. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class Transformed(val version: Int)
