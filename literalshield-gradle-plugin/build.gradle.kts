plugins { kotlin("jvm") version "2.3.21"; `java-gradle-plugin`; id("literalshield.publish") }

group = "io.github.jevhee.literalshield"
version = "0.1.0-SNAPSHOT"

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
kotlin { sourceSets.named("main") { kotlin.srcDir("../literalshield-transform/src/main/kotlin") } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    compileOnly("com.android.tools.build:gradle-api:8.13.2")
    testImplementation("com.android.tools.build:gradle:8.13.2")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}
gradlePlugin {
    plugins {
        create("literalShield") {
            id = "io.github.jevhee.literalshield"
            implementationClass = "io.github.jevhee.literalshield.gradle.LiteralShieldPlugin"
            displayName = "LiteralShield"
            description = "Project-only Android string literal obfuscation."
        }
    }
}
