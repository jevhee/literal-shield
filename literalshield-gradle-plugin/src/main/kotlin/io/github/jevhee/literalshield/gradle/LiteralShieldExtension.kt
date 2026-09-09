package io.github.jevhee.literalshield.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.*
import javax.inject.Inject

abstract class LiteralShieldExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val mode: Property<String> = objects.property(String::class.java).convention("PROTECT")
    val buildTypes: SetProperty<String> = objects.setProperty(String::class.java).convention(setOf("release"))
    val minimumLength: Property<Int> = objects.property(Int::class.java).convention(4)
    val excludePackages: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val excludeClasses: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val ignoredValues: SetProperty<String> = objects.setProperty(String::class.java).convention(emptySet())
    val includePatterns: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val buildSeed: Property<String> = objects.property(String::class.java)
    val runtimeCoordinates: Property<String> = objects.property(String::class.java)
        .convention("io.github.jevhee.literalshield:literalshield-runtime")
    val runtimeVersion: Property<String> = objects.property(String::class.java).convention("0.1.0-SNAPSHOT")
}
