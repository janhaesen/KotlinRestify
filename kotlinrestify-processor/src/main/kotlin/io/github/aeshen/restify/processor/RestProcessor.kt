package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class RestProcessor(
    private val env: SymbolProcessorEnvironment,
    private val generatedPackage: String,
) : SymbolProcessor {
    private val types = AnnotationTypeResolver()
    private val analyzer = EndpointAnalyzer()
    private val generator =
        ClientGenerator(env.codeGenerator, env.logger, generatedPackage)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        types.init(resolver)
        if (!types.isValid(env.logger)) {
            return emptyList()
        }

        val groups =
            EndpointSymbolFinder(types.restEndpoint!!)
                .find(resolver)

        groups.forEach { (container, functions) ->
            val endpoints =
                functions.mapNotNull { analyzer.analyze(it) }

            if (endpoints.isNotEmpty()) {
                generator.generate(container, endpoints)
            }
        }

        return emptyList()
    }
}
