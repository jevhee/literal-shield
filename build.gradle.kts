plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.android.dynamic-feature") version "8.13.2" apply false
    kotlin("android") version "2.3.21" apply false
}
allprojects {
    group = "io.github.jevhee.literalshield"
    version = "0.1.0-SNAPSHOT"
}
tasks.named("check") {
    dependsOn(
        ":literalshield-runtime:check",
        ":literalshield-transform:check",
        ":sample:app:assembleDebug",
        gradle.includedBuild("literalshield-gradle-plugin").task(":check"),
    )
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the runtime and Gradle plugin to the local Maven repository."
    dependsOn(
        ":literalshield-runtime:publishToMavenLocal",
        gradle.includedBuild("literalshield-gradle-plugin").task(":publishToMavenLocal"),
    )
}

if (
    providers.gradleProperty("mavenRepositoryUrl").isPresent ||
    providers.environmentVariable("MAVEN_REPOSITORY_URL").isPresent
) {
    tasks.register("publishToConfiguredMaven") {
        group = "publishing"
        description = "Publishes the runtime and Gradle plugin to the configured Maven repository."
        dependsOn(
            ":literalshield-runtime:publishAllPublicationsToRemoteRepository",
            gradle.includedBuild("literalshield-gradle-plugin")
                .task(":publishAllPublicationsToRemoteRepository"),
        )
    }
}

allprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.github.jevhee.literalshield:literalshield-runtime"))
                .using(project(":literalshield-runtime"))
        }
    }
}
