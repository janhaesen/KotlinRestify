package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import io.github.aeshen.restify.annotation.http.HttpMethod
import io.github.aeshen.restify.processor.EndpointAnalyzer

object CallGenerator {
    fun generate(
        endpoint: EndpointAnalyzer.Endpoint,
        returnTypeName: TypeName,
        params: List<KSValueParameter>,
    ): CodeBlock {
        val cb = CodeBlock.builder()
        val methodName = endpoint.method.name.uppercase()

        val requestClass = ClassName(RUNTIME_PACKAGE, "RequestData")
        val httpEnum = ClassName("$BASE_PACKAGE.annotation.http", "HttpMethod")
        val responseMapper =
            ClassName("$RUNTIME_PACKAGE.client.body", "ResponseMapper")
        val responseData = ClassName(RUNTIME_PACKAGE, "ResponseData")

        val bodyName =
            endpoint.params.body
                ?.name
                ?.asString()

        // Build combined path: include resource-level prefix if present.
        // Use the resource-level path (resourcePath) instead of the function path to avoid duplicating segments.
        val resourcePrefix = endpoint.resourcePath
        val fullPath = determineFullPath(resourcePrefix, endpoint)

        // Mapper creation (special-case Unit)
        if (returnTypeName == ClassName("kotlin", "Unit")) {
            cb.addStatement(
                "  val mapper = object : %T<%T> { override suspend fun map(response: %T) = Unit }",
                responseMapper,
                ClassName("kotlin", "Unit"),
                responseData,
            )
        } else {
            val listSerializerClass = ClassName("kotlinx.serialization.builtins", "ListSerializer")

            val isCollectionLike =
                when (returnTypeName) {
                    is ParameterizedTypeName -> collectionRawTypes.contains(returnTypeName.rawType)
                    else -> false
                }

            if (isCollectionLike) {
                val paramType = (returnTypeName as ParameterizedTypeName).typeArguments.first()
                val paramIsNullable = paramType.isNullable
                val paramNonNull =
                    if (paramIsNullable) {
                        paramType.copy(
                            nullable = false,
                        )
                    } else {
                        paramType
                    }
                val elemSerializerBlock =
                    if (paramIsNullable) {
                        CodeBlock.of("(%T.serializer()).nullable", paramNonNull)
                    } else {
                        CodeBlock.of("%T.serializer()", paramNonNull)
                    }
                cb.addStatement(
                    "  val mapper = mapperFactory.forKotlinx(%T(%L))",
                    listSerializerClass,
                    elemSerializerBlock,
                )
            } else {
                val rtIsNullable = returnTypeName.isNullable
                if (rtIsNullable) {
                    val nonNullRt = returnTypeName.copy(nullable = false)
                    val serializerBlock =
                        CodeBlock.of("(%T.serializer()).nullable", nonNullRt)
                    cb.addStatement("  val mapper = mapperFactory.forKotlinx(%L)", serializerBlock)
                } else {
                    cb.addStatement(
                        "  val mapper = mapperFactory.forKotlinx(%T.serializer())",
                        returnTypeName,
                    )
                }
            }
        }

        // Build inline call with named args and inline RequestData.build { ... }
        cb.add("  return@withContext caller.call(\n")
        cb.add("    request = %T.build {\n", requestClass)
        cb.add("      method(%T.%L)\n", httpEnum, methodName)
        cb.add("      urlPath(%S)\n", fullPath)

        // Path parameters: scan placeholders and emit buildMap or emptyMap()
        val placeholderRegex = "\\{([^}/]+)\\}".toRegex()
        val placeholders =
            placeholderRegex
                .findAll(
                    fullPath,
                ).map { it.groupValues[1] }
                .toList()
        if (placeholders.isEmpty()) {
            cb.add("      pathParameters(emptyMap())\n")
        } else {
            cb.add("      pathParameters(buildMap {\n")
            for (name in placeholders) {
                val param = params.firstOrNull { it.name?.asString() == name }
                val nullable = param?.type?.resolve()?.nullability == Nullability.NULLABLE
                if (nullable) {
                    cb.add("        if (%N != null) put(%S, %N.toString())\n", name, name, name)
                } else {
                    cb.add("        put(%S, %N.toString())\n", name, name)
                }
            }
            cb.add("      })\n")
        }

        // Query parameters: use endpoint.params.query (pairs of qname -> KSValueParameter)
        if (endpoint.params.query.isEmpty()) {
            cb.add("      queryParameters(emptyMap())\n")
        } else {
            cb.add("      queryParameters(buildMap {\n")
            endpoint.params.query.forEachIndexed { idx, (qname, qparam) ->
                val pname = qparam.name?.asString() ?: "param$idx"
                val nullable = qparam.type.resolve().nullability == Nullability.NULLABLE
                if (nullable) {
                    cb.add("        if (%N != null) put(%S, %N.toString())\n", pname, qname, pname)
                } else {
                    cb.add("        put(%S, %N.toString())\n", qname, pname)
                }
            }
            cb.add("      })\n")
        }

        // Body if applicable
        if (endpoint.method == HttpMethod.GET || endpoint.method == HttpMethod.DELETE) {
            // no body
        } else {
            if (bodyName == null) {
                cb.add("      body(null)\n")
            } else {
                cb.add("      body(%N)\n", bodyName)
            }
        }

        cb.add("    },\n")
        cb.add("    mapper = mapper\n")
        cb.add("  )\n")

        return cb.build()
    }

    private fun determineFullPath(
        resourcePrefix: String,
        endpoint: EndpointAnalyzer.Endpoint
    ): String {
        val fullPath =
            when {
                resourcePrefix.isBlank() -> endpoint.path

                endpoint.path.startsWith(resourcePrefix) -> endpoint.path

                // already includes prefix
                else -> resourcePrefix.trimEnd('/') + endpoint.path
            }
        return fullPath
    }
}
