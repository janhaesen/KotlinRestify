package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.CodeBlock

object PathGenerator {
    fun generate(
        rawPath: String,
        params: List<KSValueParameter>,
    ): CodeBlock {
        val placeholderRegex = "\\{([^}/]+)\\}".toRegex()
        val cb = CodeBlock.builder()
        cb.add("  // path template and path parameter map\n")
        cb.addStatement("  val pathTemplate = %S", rawPath)
        cb.add("  val pathParams = mutableMapOf<String, String>()\n")
        for (m in placeholderRegex.findAll(rawPath)) {
            val name = m.groupValues[1]
            val param = params.firstOrNull { it.name?.asString() == name }
            val nullable = param?.type?.resolve()?.nullability == Nullability.NULLABLE
            if (nullable) {
                cb.add("  if (%N != null) {\n", name)
                cb.addStatement("    pathParams[%S] = %N.toString()", name, name)
                cb.add("  }\n")
            } else {
                cb.addStatement("  pathParams[%S] = %N.toString()", name, name)
            }
        }
        return cb.build()
    }
}
