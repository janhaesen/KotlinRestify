package io.github.aeshen.restify.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSValueParameter

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

    fun analyze(fn: KSFunctionDeclaration): Endpoint? {
        val endpointAnno =
            fn.annotations.firstOrNull {
                it.shortName.asString() == "RestEndpoint"
            } ?: return null

        val method =
            (endpointAnno.arguments.first { it.name?.asString() == "method" }.value as KSName)
                .getShortName()

        val path =
            endpointAnno.arguments.first { it.name?.asString() == "path" }.value as String

        return Endpoint(fn, method, path, analyzeParams(fn))
    }

    private fun analyzeParams(fn: KSFunctionDeclaration): ParameterAnalysis {
        val path = mutableListOf<KSValueParameter>()
        val query = mutableListOf<Pair<String, KSValueParameter>>()
        var body: KSValueParameter? = null

        fn.parameters.forEach { p ->
            when {
                p.has("QueryParam") -> {
                    val name =
                        p.annotations
                            .first { it.shortName.asString() == "QueryParam" }
                            .arguments
                            .first { it.name?.asString() == "name" }
                            .value as String
                    query += name to p
                }

                p.has("Body") -> {
                    body = p
                }

                else -> {
                    path += p
                }
            }
        }

        return ParameterAnalysis(path, query, body)
    }

    private fun KSValueParameter.has(name: String) = annotations.any { it.shortName.asString() == name }
}
