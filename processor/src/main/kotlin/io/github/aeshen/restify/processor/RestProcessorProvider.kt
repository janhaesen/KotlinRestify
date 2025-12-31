package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class RestProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        // Optional: read a custom argument that the convention plugin may pass
        val generatedPkg =
            environment.options["restify.generatedPackage"]
                ?: "io.github.aeshen.restify.generated"
        environment.logger.info("RestProcessor will generate code into package: $generatedPkg")
        return RestProcessor(environment, generatedPkg)
    }
}
