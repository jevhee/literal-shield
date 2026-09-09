package io.github.jevhee.literalshield.gradle

import com.android.build.api.instrumentation.*
import io.github.jevhee.literalshield.transform.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor

interface ShieldParameters : InstrumentationParameters {
    @get:Input val module: Property<String>
    @get:Input val variant: Property<String>
    @get:Input val seed: Property<String>
    @get:Input val minimumLength: Property<Int>
    @get:Input val excludePackages: ListProperty<String>
    @get:Input val excludeClasses: ListProperty<String>
    @get:Input val ignoredValues: SetProperty<String>
    @get:Input val includePatterns: ListProperty<String>
}

abstract class ShieldVisitorFactory : AsmClassVisitorFactory<ShieldParameters> {
    override fun isInstrumentable(classData: ClassData): Boolean =
        !LiteralPolicy(config()).excludedClass(classData.className.replace('.', '/'))

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        val params = parameters.get()
        return LiteralTransformer(config()).visitor(nextClassVisitor, params.module.get(), params.variant.get(), params.seed.get())
    }

    private fun config(): ShieldConfig = parameters.get().let {
        ShieldConfig(it.minimumLength.get(), it.excludePackages.get(), it.excludeClasses.get(),
            it.ignoredValues.get(), it.includePatterns.get())
    }
}
