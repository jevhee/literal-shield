plugins { kotlin("jvm"); `java-library` }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":literalshield-runtime"))
    api("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-util:9.8")
}
