package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import io.github.aeshen.restify.processor.EndpointAnalyzer

object QueryGenerator {
    fun generate(
        endpoint: EndpointAnalyzer.Endpoint,
        params: List<KSValueParameter>,
    ): CodeBlock {
        if (endpoint.params.query.isEmpty()) {
            return CodeBlock.of("val queryParams = emptyMap<String, String?>()\n")
        }

        val cb = CodeBlock.builder()
        cb.add("  // query parameter map (nullable values retained for UrlBuilder)\n")
        cb.add("  val queryParams = mutableMapOf<String, String?>()\n")
        endpoint.params.query.forEachIndexed { idx, (qname, qParam) ->
            val pName = qParam.argNameOr("param$idx")
            val nullable = qParam.isNullableParam()
            if (nullable) {
                cb.beginControlFlow("if (%N != null)", pName)
                cb.addStatement("queryParams[%S] = %N.toString()", qname, pName)
                cb.endControlFlow()
            } else {
                cb.addStatement("queryParams[%S] = %N.toString()", qname, pName)
            }
        }

        return cb.build()
    }
}
