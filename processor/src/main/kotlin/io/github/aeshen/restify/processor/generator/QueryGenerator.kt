package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.CodeBlock
import io.github.aeshen.restify.processor.EndpointAnalyzer

object QueryGenerator {
    fun generate(
        endpoint: EndpointAnalyzer.Endpoint,
        params: List<KSValueParameter>,
    ): CodeBlock {
        if (endpoint.params.query.isEmpty()) {
            return CodeBlock.of("  val queryParams = emptyMap<String, String?>()\n")
        }

        val cb = CodeBlock.builder()
        cb.add("  // query parameter map (nullable values retained for UrlBuilder)\n")
        cb.add("  val queryParams = mutableMapOf<String, String?>()\n")
        endpoint.params.query.forEachIndexed { idx, (qname, qparam) ->
            val pname = qparam.name?.asString() ?: "param$idx"
            val nullable = qparam.type.resolve().nullability == Nullability.NULLABLE
            if (nullable) {
                cb.add("  if (%N != null) queryParams[%S] = %N.toString()\n", pname, qname, pname)
            } else {
                cb.addStatement("  queryParams[%S] = %N.toString()", qname, pname)
            }
        }
        return cb.build()
    }
}
