import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

plugins {
    `maven-publish`
}

val publicationGroup = providers.gradleProperty("GROUP")
    .orElse("io.github.jevhee.literalshield")
val publicationArtifact = providers.gradleProperty("POM_ARTIFACT_ID")
    .orElse(project.name)
val publicationVersion = providers.environmentVariable("VERSION")
    .orElse(providers.gradleProperty("VERSION_NAME"))
    .orElse("0.1.0-SNAPSHOT")

group = publicationGroup.get()
version = publicationVersion.get()

extensions.configure<PublishingExtension> {
    publications.withType(MavenPublication::class.java).configureEach {
        groupId = project.group.toString()
        artifactId = publicationArtifact.get()
        version = project.version.toString()
        pom {
            name.set(providers.gradleProperty("POM_NAME").orElse("LiteralShield ${project.name}"))
            description.set(
                providers.gradleProperty("POM_DESCRIPTION")
                    .orElse("Project-only Android string literal obfuscation."),
            )
            providers.gradleProperty("POM_URL").orNull?.let(url::set)
            licenses {
                license {
                    name.set(providers.gradleProperty("POM_LICENSE_NAME").orElse("Apache License, Version 2.0"))
                    url.set(
                        providers.gradleProperty("POM_LICENSE_URL")
                            .orElse("https://www.apache.org/licenses/LICENSE-2.0.txt"),
                    )
                    distribution.set("repo")
                }
            }
            providers.gradleProperty("POM_DEVELOPER_ID").orNull?.let { developerId ->
                developers { developer { id.set(developerId); name.set(providers.gradleProperty("POM_DEVELOPER_NAME").orNull) } }
            }
            providers.gradleProperty("POM_SCM_URL").orNull?.let { scmUrl ->
                scm {
                    url.set(scmUrl)
                    connection.set(providers.gradleProperty("POM_SCM_CONNECTION").orNull)
                    developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION").orNull)
                }
            }
        }
    }
}
