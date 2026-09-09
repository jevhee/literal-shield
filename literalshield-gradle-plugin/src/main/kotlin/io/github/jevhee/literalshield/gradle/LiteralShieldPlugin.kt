package io.github.jevhee.literalshield.gradle

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import io.github.jevhee.literalshield.transform.ShieldConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

class LiteralShieldPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("literalShield", LiteralShieldExtension::class.java)
        val invocationSeed = project.providers.of(InvocationSeed::class.java) {}
        val aggregate = project.tasks.register("literalShieldReport") {
            it.group = "verification"
            it.description = "Scan selected variants. Configure literalShield.mode = REPORT first."
        }
        var configured = false
        listOf("com.android.application", "com.android.library", "com.android.dynamic-feature").forEach { androidId ->
            project.pluginManager.withPlugin(androidId) {
                if (!configured) {
                    configured = true
                    val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
                    components.onVariants(components.selector().all()) { variant ->
                        require(extension.mode.get() in setOf("PROTECT", "REPORT")) { "LiteralShield: mode must be PROTECT or REPORT" }
                        ShieldConfig(extension.minimumLength.get(), extension.excludePackages.get(),
                            extension.excludeClasses.get(), extension.ignoredValues.get(), extension.includePatterns.get())
                        if (extension.enabled.get() && variant.buildType in extension.buildTypes.get()) {
                            val report = project.tasks.register("literalShieldReport${variant.name.replaceFirstChar { it.uppercaseChar() }}", ShieldReportTask::class.java) {
                                it.module.set(project.path); it.variant.set(variant.name)
                                it.scanMode.set(extension.mode)
                                it.minimumLength.set(extension.minimumLength)
                                it.excludePackages.set(extension.excludePackages); it.excludeClasses.set(extension.excludeClasses)
                                it.ignoredValues.set(extension.ignoredValues); it.includePatterns.set(extension.includePatterns)
                                it.outputFile.set(project.layout.buildDirectory.file("reports/literalshield/${variant.name}/scan.json"))
                            }
                            variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(report)
                                .toGet(ScopedArtifact.CLASSES, ShieldReportTask::jars, ShieldReportTask::directories)
                            aggregate.configure { it.dependsOn(report) }
                            if (extension.mode.get() == "PROTECT") {
                                val seed = extension.buildSeed.orElse(invocationSeed).get()
                                project.dependencies.add(
                                    "${variant.name}Implementation",
                                    "${extension.runtimeCoordinates.get()}:${extension.runtimeVersion.get()}",
                                )
                                variant.instrumentation.transformClassesWith(ShieldVisitorFactory::class.java, InstrumentationScope.PROJECT) {
                                    it.module.set(project.path); it.variant.set(variant.name); it.seed.set(seed)
                                    it.minimumLength.set(extension.minimumLength)
                                    it.excludePackages.set(extension.excludePackages); it.excludeClasses.set(extension.excludeClasses)
                                    it.ignoredValues.set(extension.ignoredValues); it.includePatterns.set(extension.includePatterns)
                                }
                                if (androidId == "com.android.library") {
                                    val normalize = project.tasks.register("literalShieldNormalize${variant.name.replaceFirstChar { it.uppercaseChar() }}Aar", NormalizeClassesTask::class.java)
                                    variant.artifacts.use(normalize)
                                        .wiredWithFiles(NormalizeClassesTask::inputArchive, NormalizeClassesTask::outputArchive)
                                        .toTransform(SingleArtifact.AAR)
                                }
                                variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
                            }
                        }
                    }
                }
            }
        }
        project.afterEvaluate {
            check(configured) { "LiteralShield requires an Android application, library, or dynamic-feature plugin" }
        }
    }

}
