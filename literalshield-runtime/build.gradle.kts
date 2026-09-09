plugins { kotlin("jvm"); id("literalshield.publish") }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
publishing { publications { create<MavenPublication>("runtime") { from(components["java"]) } } }
