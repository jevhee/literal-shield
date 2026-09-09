pluginManagement {
    includeBuild("build-logic")
    includeBuild("literalshield-gradle-plugin")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "literal-shield"
include(
    ":literalshield-runtime",
    ":literalshield-transform",
    ":sample:app",
    ":sample:core-network",
    ":sample:android-library",
    ":sample:feature_login",
)
project(":sample:feature_login").projectDir = file("sample/feature-login")
