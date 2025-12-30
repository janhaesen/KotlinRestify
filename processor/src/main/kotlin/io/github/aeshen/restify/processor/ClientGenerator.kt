package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
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

        val httpClientParam =
            ParameterSpec
                .builder(
                    "http",
                    ClassName("io.github.ashen.restify.http", "HttpClient"),
                ).build()

        val clientClass =
            TypeSpec
                .classBuilder(clientClassName)
                .primaryConstructor(
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
            clientClass.addFunction(buildFunction(endpoint))
        }

        fileBuilder.addType(clientClass.build())

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

        fn.returnType?.let {
            funBuilder.returns(it.toTypeName())
        }

        fn.parameters.forEach { param ->
            funBuilder.addParameter(
                param.name?.asString() ?: "param",
                param.type.toTypeName(),
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

    private fun buildUrlExpression(
        rawPath: String,
        params: EndpointAnalyzer.ParameterAnalysis,
    ): String {
        var urlExpr = "\"$rawPath\""

        // Replace path variables: "/users/{id}" -> "/users/${id}"
        params.path.forEach { param ->
            val name = param.name?.asString() ?: return@forEach
            urlExpr =
                urlExpr.replace(
                    "{$name}",
                    "\${$name}",
                )
        }

        // Append query parameters
        if (params.query.isNotEmpty()) {
            val queryString =
                params.query.joinToString("&") { (name, param) ->
                    "$name=\${${param.name?.asString()}}"
                }

            urlExpr = "\"${'$'}{$urlExpr}?$queryString\""
        }

        return urlExpr
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
        val deps =
            Dependencies(
                aggregating = false,
                *sourceFiles.toTypedArray(),
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
}
