package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import kotlin.collections.get

object PathGenerator {
    fun generate(
        rawPath: String,
        params: List<KSValueParameter>,
    ): CodeBlock {
        val cb = CodeBlock.builder()

        cb.add("  // path template and path parameter map\n")
        cb.addStatement("val pathTemplate = %S", rawPath)
        cb.add("val pathParams = mutableMapOf<String, String>()\n")

        for (m in placeholderRegex.findAll(rawPath)) {
            val name = m.groupValues[1]
            val matched = findMatchingParam(name, params)
            val argName = matched.argNameOr(name)
            val nullable = matched.isNullableParam()

            if (nullable) {
                cb.beginControlFlow("if (%N != null)", argName)
                cb.addStatement("pathParams[%S] = %N.toString()", name, argName)
                cb.endControlFlow()
            } else {
                cb.addStatement("pathParams[%S] = %N.toString()", name, argName)
            }
        }

        return cb.build()
    }
}
