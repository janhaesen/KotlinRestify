package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability

/**
 * Single-responsibility validator that enforces endpoint-level correctness rules.
 * Keeps validation separate from analysis so rules can be extended/covered independently.
 */
class EndpointValidator {
    /**
     * Returns true when endpoint is valid; emits errors through logger when not.
     */
    fun validate(
        endpoint: EndpointAnalyzer.Endpoint,
        types: AnnotationTypeResolver.ResolvedAnnotationTypes,
        logger: KSPLogger,
    ): Boolean {
        var ok = true
        val fnName = endpoint.function.simpleName.asString()

        // Body count check: more than one is invalid
        val bodyCount =
            endpoint.function.parameters.count { p ->
                types.bodyAnno?.let { paramHasAnnotation(p, it) } ?: false
            }
        if (bodyCount > 1) {
            logger.error(
                "Endpoint $fnName has more than one @Body parameter; exactly one is allowed",
            )
            ok = false
        }

        // Query required => non-nullable check
        endpoint.params.query.forEach { (name, param) ->
            val ann = firstMatchingAnnotation(param, listOfNotNull(types.queryAnno))
            val required = ann?.getBooleanArg("required") ?: false
            if (required) {
                val nullability = param.type.resolve().nullability
                if (nullability == Nullability.NULLABLE) {
                    logger.error(
                        "Query parameter '$name' on $fnName is marked required but the parameter type is nullable",
                    )
                    ok = false
                }
            }
        }

        // Path placeholder validation: extract {placeholders} from endpoint.path
        val placeholderRegex = "\\{([^}/]+)\\}".toRegex()
        val placeholders = placeholderRegex.findAll(endpoint.path).map { it.groupValues[1] }.toSet()

        val pathParamNames =
            endpoint.params.path
                .mapNotNull { p ->
                    // prefer explicit @Path('name') argument; fallback to parameter name
                    val ann = types.pathAnno?.let { firstMatchingAnnotation(p, listOf(it)) }
                    ann?.getStringArg("name") ?: p.name?.asString()
                }.toSet()

        val missing = placeholders - pathParamNames
        if (missing.isNotEmpty()) {
            logger.error(
                "Endpoint $fnName path placeholders ${missing.joinToString(
                    ", ",
                )} have no matching @Path parameters",
            )
            ok = false
        }

        val extra = pathParamNames - placeholders
        if (extra.isNotEmpty()) {
            logger.error(
                "Endpoint $fnName has @Path parameters ${extra.joinToString(
                    ", ",
                )} that are not present in the path string",
            )
            ok = false
        }

        return ok
    }

    // small reuse helpers (similar to analyzer but kept here to avoid analyzer dependency)
    private fun paramHasAnnotation(
        param: KSValueParameter,
        ksType: KSType,
    ): Boolean =
        param.annotations.any { ann ->
            ann.annotationType.resolve().declaration ==
                ksType.declaration
        }

    private fun firstMatchingAnnotation(
        param: KSValueParameter,
        candidates: List<KSType>,
    ): KSAnnotation? =
        param.annotations.firstOrNull { ann ->
            val decl = ann.annotationType.resolve().declaration
            candidates.any { it.declaration == decl }
        }

    private fun KSAnnotation.getStringArg(name: String): String? =
        this.arguments.firstOrNull { it.name?.asString() == name }?.value as? String

    private fun KSAnnotation.getBooleanArg(name: String): Boolean? =
        this.arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean
}
