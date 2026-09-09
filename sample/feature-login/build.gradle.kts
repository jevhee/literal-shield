plugins { id("com.android.dynamic-feature"); kotlin("android"); id("io.github.jevhee.literalshield") }
android {
    namespace = "io.github.jevhee.literalshield.sample.feature"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
literalShield {
    enabled.set(!providers.gradleProperty("shieldDisabled").isPresent && !providers.gradleProperty("appOnly").isPresent)
    mode.set(providers.gradleProperty("shieldMode").orElse("PROTECT"))
    if (!providers.gradleProperty("randomSeed").isPresent) buildSeed.set(providers.gradleProperty("shieldSeed").orElse("sample-reproducible-v1"))
}
dependencies { implementation(project(":sample:app")) }
