package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class RestProcessor(
    private val env: SymbolProcessorEnvironment,
    private val generatedPackage: String,
    private val types: AnnotationTypeResolver = AnnotationTypeResolver(),
    private val analyzer: EndpointAnalyzer = EndpointAnalyzer(),
    private val generator: ClientGenerator = ClientGenerator(env.codeGenerator, env.logger, generatedPackage),
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        types.init(resolver)

        val resolvedTypes =
            types.requireResolvedTypes(env.logger)
                // requireResolvedTypes already logged an error
                ?: return emptyList()

        // Prefer the non-nullable ResolvedAnnotationTypes constructor so the finder doesn't need to re-check.
        // Backwards compatibility is preserved because EndpointSymbolFinder still exposes the old constructor.
        val groups = EndpointSymbolFinder(resolvedTypes).find(resolver)
        env.logger.info("Found ${groups.size} endpoint container(s) with annotated functions")

        val validator = EndpointValidator()

        groups.forEach { (container, functions) ->
            val endpoints = functions.mapNotNull { analyzer.analyze(it, resolvedTypes) }
            env.logger.info("Found ${endpoints.size} endpoints in container $container")

            val validEndpoints =
                endpoints.filter { ep ->
                    val ok = validator.validate(ep, resolvedTypes, env.logger)
                    if (!ok) {
                        env.logger.error("Skipping generation for container $container due to validation errors")
                    }
                    ok
                }

            if (validEndpoints.isNotEmpty()) {
                generator.generate(container, validEndpoints)
            }
        }

        return emptyList()
    }
}
