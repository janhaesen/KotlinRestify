package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
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
        val responseMapper = ClassName("$RUNTIME_PACKAGE.client.body", "ResponseMapper")
        val responseData = ClassName(RUNTIME_PACKAGE, "ResponseData")

        val bodyName = endpoint.params.body?.name?.asString()

        val resourcePrefix = endpoint.resourcePath
        val fullPath = determineFullPath(resourcePrefix, endpoint)

        // Mapper creation (special-case Unit)
        if (returnTypeName == ClassName("kotlin", "Unit")) {
            cb.addStatement(
                "val mapper = object : %T<%T> { override suspend fun map(response: %T) = Unit }",
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
                        paramType.copy(nullable = false)
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
                    "val mapper = mapperFactory.forKotlinx(%T(%L))",
                    listSerializerClass,
                    elemSerializerBlock,
                )
            } else {
                val rtIsNullable = returnTypeName.isNullable
                if (rtIsNullable) {
                    val nonNullRt = returnTypeName.copy(nullable = false)
                    val serializerBlock = CodeBlock.of("(%T.serializer()).nullable", nonNullRt)
                    cb.addStatement("val mapper = mapperFactory.forKotlinx(%L)", serializerBlock)
                } else {
                    cb.addStatement(
                        "val mapper = mapperFactory.forKotlinx(%T.serializer())",
                        returnTypeName,
                    )
                }
            }
        }

        // Start caller.call with inline RequestData.build { ... }
        cb.add("return@withContext caller.call(\n")
        cb.add("  request = %T.build {\n", requestClass)
        cb.add("    method(%T.%L)\n", httpEnum, methodName)
        cb.add("    urlPath(%S)\n", fullPath)

        // Path parameters block as nested CodeBlock (safer formatting)
        val placeholders = placeholderRegex.findAll(fullPath).map { it.groupValues[1] }.toList()

        val pathParamsBlock = CodeBlock.builder()
        if (placeholders.isEmpty()) {
            pathParamsBlock.add("    pathParameters(emptyMap())\n")
        } else {
            pathParamsBlock.add("    pathParameters(buildMap {\n")
            for (ph in placeholders) {
                val matchedParam = findMatchingParam(ph, params)
                val argName = matchedParam.argNameOr(ph)
                val nullable = matchedParam.isNullableParam()
                if (nullable) {
                    pathParamsBlock.add("      if (%N != null) put(%S, %N.toString())\n", argName, ph, argName)
                } else {
                    pathParamsBlock.add("      put(%S, %N.toString())\n", ph, argName)
                }
            }
            pathParamsBlock.add("    })\n")
        }
        cb.add("%L", pathParamsBlock.build())

        // Query parameters: build similarly
        val queryParamsBlock = CodeBlock.builder()
        if (endpoint.params.query.isEmpty()) {
            queryParamsBlock.add("    queryParameters(emptyMap())\n")
        } else {
            queryParamsBlock.add("    queryParameters(buildMap {\n")
            endpoint.params.query.forEachIndexed { idx, (qname, qparam) ->
                val pname = qparam.argNameOr("param$idx")
                val nullable = qparam.isNullableParam()
                if (nullable) {
                    queryParamsBlock.add("      if (%N != null) put(%S, %N.toString())\n", pname, qname, pname)
                } else {
                    queryParamsBlock.add("      put(%S, %N.toString())\n", qname, pname)
                }
            }
            queryParamsBlock.add("    })\n")
        }
        cb.add("%L", queryParamsBlock.build())

        // Body if applicable
        if (endpoint.method == HttpMethod.GET || endpoint.method == HttpMethod.DELETE) {
            // no body
        } else {
            if (bodyName == null) {
                cb.add("    body(null)\n")
            } else {
                cb.add("    body(%N)\n", bodyName)
            }
        }

        cb.add("  },\n")
        cb.add("  mapper = mapper\n")
        cb.add(")\n")

        return cb.build()
    }

    private fun determineFullPath(
        resourcePrefix: String,
        endpoint: EndpointAnalyzer.Endpoint
    ): String {
        val fullPath = when {
            resourcePrefix.isBlank() -> endpoint.path
            endpoint.path.startsWith(resourcePrefix) -> endpoint.path
            else -> resourcePrefix.trimEnd('/') + endpoint.path
        }
        return fullPath
    }
}
