plugins {
    id("com.android.application")
    kotlin("android")
    id("io.github.jevhee.literalshield")
}
android {
    namespace = "io.github.jevhee.literalshield.sample"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.jevhee.literalshield.sample"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    dynamicFeatures += setOf(":sample:feature_login")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
literalShield {
    enabled.set(!providers.gradleProperty("shieldDisabled").isPresent)
    mode.set(providers.gradleProperty("shieldMode").orElse("PROTECT"))
    if (!providers.gradleProperty("randomSeed").isPresent) buildSeed.set(providers.gradleProperty("shieldSeed").orElse("sample-reproducible-v1"))
}
dependencies {
    implementation(project(":sample:core-network"))
    implementation(project(":sample:android-library"))
}
