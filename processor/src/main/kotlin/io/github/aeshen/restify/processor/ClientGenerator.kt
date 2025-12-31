package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.aeshen.restify.annotation.http.HttpMethod

class ClientGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val generatedPackage: String,
) {
    fun generate(
        containerName: String,
        endpoints: List<EndpointAnalyzer.Endpoint>,
    ) {
        val clientClassName =
            "${containerName.substringAfterLast('.')}Client"

        val fileBuilder =
            FileSpec.builder(generatedPackage, clientClassName)

        // Corrected package FQN (fixed typo: io.github.ashen -> io.github.aeshen)
        val httpClientParam =
            ParameterSpec
                .builder(
                    "http",
                    ClassName("io.github.aeshen.kotlinrestify.runtime.client", "HttpClient"),
                ).build()

        val clientClassBuilder =
            TypeSpec
                .classBuilder(clientClassName)
                // annotate generated class for tooling and consumers
                .addAnnotation(
                    AnnotationSpec
                        .builder(
                            ClassName("javax.annotation.processing", "Generated"),
                        ).addMember("%S", "KotlinRestifyProcessor")
                        .build(),
                ).primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter(httpClientParam)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("http", httpClientParam.type)
                        .initializer("http")
                        .addModifiers(KModifier.PRIVATE)
                        .build(),
                )

        endpoints.forEach { endpoint ->
            clientClassBuilder.addFunction(buildFunction(endpoint))
        }

        fileBuilder.addType(clientClassBuilder.build())

        writeFile(
            fileBuilder.build(),
            endpoints.mapNotNull { it.function.containingFile },
        )

        logger.info("Generated $generatedPackage.$clientClassName")
    }

    private fun buildFunction(endpoint: EndpointAnalyzer.Endpoint): FunSpec {
        val fn = endpoint.function

        val funBuilder =
            FunSpec
                .builder(fn.simpleName.asString())
                .addModifiers(KModifier.SUSPEND)

        // safe return-type conversion (guard against KSP 'error type' during incremental rounds)
        fn.returnType?.let { rt ->
            val returnTypeName = safeToTypeName(rt)
            funBuilder.returns(returnTypeName)
        }

        fn.parameters.forEach { param ->
            val paramType = safeToTypeName(param.type)
            funBuilder.addParameter(
                param.name?.asString() ?: "param",
                paramType,
            )
        }

        val urlExpression =
            buildUrlExpression(endpoint.path, endpoint.params)

        val httpCall =
            buildHttpCall(
                endpoint.method.let { HttpMethod.valueOf(it) },
                urlExpression,
                endpoint.params.body,
            )

        funBuilder.addStatement("return %L", httpCall)

        return funBuilder.build()
    }

    // Build URL expression using concatenation to avoid escaping template syntax.
    // Example: "/users/{id}/photos" -> "\"/users/\" + id + \"/photos\""
    private fun buildUrlExpression(
        rawPath: String,
        params: EndpointAnalyzer.ParameterAnalysis,
    ): String {
        val placeholderRegex = "\\{([^}/]+)\\}".toRegex()
        var lastIndex = 0
        val parts = mutableListOf<String>()

        for (m in placeholderRegex.findAll(rawPath)) {
            val start = m.range.first
            val end = m.range.last + 1
            val literal = rawPath.substring(lastIndex, start)
            if (literal.isNotEmpty()) {
                parts += "\"${literal}\""
            }
            val name = m.groupValues[1]
            parts += name // parameter insertion (unquoted)
            lastIndex = end
        }

        val tail = rawPath.substring(lastIndex)
        if (tail.isNotEmpty()) {
            parts += "\"${tail}\""
        }

        var expr = if (parts.isEmpty()) "\"$rawPath\"" else parts.joinToString(" + ")

        // Append query parameters using concatenation: ?a= + param
        if (params.query.isNotEmpty()) {
            val qsParts =
                params.query.mapIndexed { idx, (name, param) ->
                    val paramExpr = param.name?.asString() ?: "param$idx"
                    val prefix = if (idx == 0) "\"?$name=\" + $paramExpr" else "\"&$name=\" + $paramExpr"
                    prefix
                }
            // join queries with " + " and append to expr
            expr = if (expr.isBlank()) qsParts.joinToString(" + ") else "$expr + ${qsParts.joinToString(" + ")}"
        }

        return expr
    }

    private fun buildHttpCall(
        httpMethod: HttpMethod,
        urlExpression: String,
        bodyParam: KSValueParameter?,
    ): String =
        when (httpMethod) {
            HttpMethod.GET -> {
                "http.get($urlExpression)"
            }

            HttpMethod.DELETE -> {
                "http.delete($urlExpression)"
            }

            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            -> {
                val body =
                    bodyParam?.name?.asString() ?: "null"
                "http.${httpMethod.name.lowercase()}($urlExpression, $body)"
            }

            else -> {
                logger.error("Unsupported HTTP method: $httpMethod")
                "error(\"Unsupported HTTP method\")"
            }
        }

    private fun writeFile(
        fileSpec: FileSpec,
        sourceFiles: List<KSFile>,
    ) {
        // guard empty sourceFiles and prefer aggregating=true for broader incremental correctness
        val srcs = sourceFiles.toTypedArray()
        val deps =
            Dependencies(
                aggregating = true,
                *srcs,
            )

        codeGenerator
            .createNewFile(
                deps,
                generatedPackage,
                fileSpec.name,
            ).bufferedWriter()
            .use { writer ->
                fileSpec.writeTo(writer)
            }
    }

    // Safe conversion helper: try KSP->TypeName conversion, fall back to ClassName.bestGuess or kotlin.Any
    private fun safeToTypeName(typeRef: KSTypeReference?): TypeName {
        if (typeRef == null) return ClassName("kotlin", "Unit")
        return try {
            typeRef.toTypeName()
        } catch (_: IllegalArgumentException) {
            // unresolved / error type — try to recover using declaration FQN
            val resolved =
                try {
                    typeRef.resolve()
                } catch (_: Exception) {
                    null
                }
            val fqn = resolved?.declaration?.qualifiedName?.asString()
            val base = if (!fqn.isNullOrBlank()) ClassName.bestGuess(fqn) else ClassName("kotlin", "Any")
            if (resolved?.nullability == Nullability.NULLABLE) {
                base.copy(nullable = true)
            } else {
                base
            }
        }
    }
}
