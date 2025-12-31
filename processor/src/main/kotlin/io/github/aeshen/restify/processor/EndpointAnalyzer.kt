package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability

class EndpointAnalyzer {
    data class Endpoint(
        val function: KSFunctionDeclaration,
        val method: String,
        val path: String,
        val params: ParameterAnalysis,
    )

    data class ParameterAnalysis(
        val path: List<KSValueParameter>,
        val query: List<Pair<String, KSValueParameter>>,
        val body: KSValueParameter?,
    )

    /**
     * Analyze function and return Endpoint or null if not an endpoint.
     * This variant requires the resolved annotation types view (non-nullable) from AnnotationTypeResolver.
     */
    fun analyze(
        fn: KSFunctionDeclaration,
        types: AnnotationTypeResolver.ResolvedAnnotationTypes,
    ): Endpoint? {
        // find an HTTP method annotation on the function (resolved via AnnotationTypeResolver)
        val methodAnno =
            fn.annotations.firstOrNull { ann ->
                types.httpMethodAnnos.values.any { it.declaration == ann.annotationType.resolve().declaration }
            } ?: return null

        // determine the HTTP method key (e.g. "GET", "POST") by matching declaration equality
        val annoDecl = methodAnno.annotationType.resolve().declaration
        val methodKey =
            types.httpMethodAnnos.entries
                .firstOrNull { it.value.declaration == annoDecl }
                ?.key ?: "GET"

        // read path safely from the HTTP method annotation (fallback to "/" when absent)
        val path = methodAnno.getStringArg("path") ?: "/"

        return Endpoint(fn, methodKey, path, analyzeParams(fn, types))
    }

    private fun analyzeParams(
        fn: KSFunctionDeclaration,
        types: AnnotationTypeResolver.ResolvedAnnotationTypes,
    ): ParameterAnalysis {
        val pathParams = mutableListOf<KSValueParameter>()
        val queryParams = mutableListOf<Pair<String, KSValueParameter>>()
        var bodyParam: KSValueParameter? = null

        fn.parameters.forEach { p ->
            // prefer declaration-equality checks via helper functions
            when {
                types.bodyAnno != null && hasAnnotation(p, types.bodyAnno) -> {
                    bodyParam = p
                }

                types.pathAnno != null && hasAnnotation(p, types.pathAnno) -> {
                    pathParams += p
                }

                (types.queryAnno != null && hasAnnotation(p, types.queryAnno)) -> {
                    // determine query parameter name: annotation 'name' argument if present, else parameter name
                    val ann = firstMatchingAnnotation(p, listOfNotNull(types.queryAnno))
                    val name = ann?.getStringArg("name") ?: p.name?.asString() ?: "param"

                    queryParams += name to p
                }

                else -> {
                    // fallback: treat as path param when no explicit annotation (legacy behaviour)
                    pathParams += p
                }
            }
        }

        return ParameterAnalysis(pathParams, queryParams, bodyParam)
    }

    private fun hasAnnotation(
        param: KSValueParameter,
        ksType: KSType?,
    ): Boolean =
        ksType != null &&
            param.annotations.any { ann -> ann.annotationType.resolve().declaration == ksType.declaration }

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
            logger.error("Endpoint $fnName has more than one @Body parameter; exactly one is allowed")
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
                "Endpoint $fnName path placeholders ${missing.joinToString(", ")} have no matching @Path parameters",
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
    ): Boolean = param.annotations.any { ann -> ann.annotationType.resolve().declaration == ksType.declaration }

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
