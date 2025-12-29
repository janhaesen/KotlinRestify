package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

class RestProcessor(
    env: SymbolProcessorEnvironment,
    private val generatedPackage: String,
) : SymbolProcessor {
    private val logger: KSPLogger = env.logger
    private val codeGenerator: CodeGenerator = env.codeGenerator

    // -------------------------------------------------------------------------
    // 1️⃣  Helper to resolve the annotation types **once** (lazy, using the
    //     Resolver that is supplied to `process`).  We keep them as nullable
    //     properties because we cannot access the Resolver until `process`
    //     is invoked.
    // -------------------------------------------------------------------------
    private var restEndpointType: KSType? = null
    private var queryParamType: KSType? = null
    private var bodyType: KSType? = null

    /** Populate the cached KSType references – called the first time `process` runs. */
    private fun initTypes(resolver: Resolver) {
        if (restEndpointType != null) {
            return // already initialised
        }

        // Fully‑qualified names of the annotations (they live in the annotations module)
        val restEndpointFqn = "io.github.aeshen.restify.annotation.RestEndpoint"
        val queryParamFqn = "io.github.aeshen.restify.annotation.QueryParam"
        val bodyFqn = "io.github.aeshen.restify.annotation.Body"

        restEndpointType =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(restEndpointFqn),
                )?.asStarProjectedType()

        queryParamType =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(queryParamFqn),
                )?.asStarProjectedType()

        bodyType =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(bodyFqn),
                )?.asStarProjectedType()
    }

    // -------------------------------------------------------------------------
    // 2️⃣  Main processing entry point – this is where the Resolver is supplied.
    // -------------------------------------------------------------------------
    override fun process(resolver: Resolver): List<KSAnnotated> {
        initTypes(resolver)

        if (!validateSetup()) {
            return emptyList()
        }

        processEndpoints(resolver)
        return emptyList()
    }

    private fun validateSetup(): Boolean =
        if (restEndpointType == null) {
            logger.error("Unable to locate RestEndpoint annotation. Did you forget to add the annotations module?")
            false
        } else {
            true
        }

    private fun processEndpoints(resolver: Resolver) {
        val endpointFunctions =
            resolver
                .getSymbolsWithAnnotation(
                    restEndpointType!!.declaration.qualifiedName!!.asString(),
                ).filterIsInstance<KSFunctionDeclaration>()

        if (!endpointFunctions.iterator().hasNext()) {
            return
        }

        val groups =
            endpointFunctions.groupBy { fn ->
                fn.parentDeclaration?.qualifiedName?.asString() ?: "<top‑level>"
            }

        groups.forEach { (containerName, functions) ->
            generateClient(containerName, functions)
        }
    }

    // -------------------------------------------------------------------------
    // 5️⃣  Code‑generation helper – creates a thin client wrapper for a group.
    // -------------------------------------------------------------------------
    private fun generateClient(
        containerName: String,
        functions: List<KSFunctionDeclaration>,
    ) {
        // Name of the generated client class (e.g. `UserServiceClient`)
        val clientClassName = "${containerName.substringAfterLast('.')}Client"

        // Build the Kotlin file
        val fileBuilder = FileSpec.builder(generatedPackage, clientClassName)

        // -----------------------------------------------------------------
        // 5.1  Define the constructor that receives an HttpClient (you’ll provide the interface later)
        // -----------------------------------------------------------------
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

        // -----------------------------------------------------------------
        // 5.2  For each annotated function, generate a suspend wrapper that calls the HttpClient
        // -----------------------------------------------------------------
        functions.forEach { fn ->
            generateFunctionStub(fn)?.let { clientClass.addFunction(it) }
        }

        fileBuilder.addType(clientClass.build())

        // -----------------------------------------------------------------
        // 5.3  Write the file to the generated sources directory
        // -----------------------------------------------------------------
        val fileSpec = fileBuilder.build()
        val deps =
            Dependencies(
                aggregating = false,
                *functions.mapNotNull { it.containingFile }.toTypedArray(),
            )
        codeGenerator
            .createNewFile(deps, generatedPackage, clientClassName)
            .bufferedWriter()
            .use { writer -> fileSpec.writeTo(writer) }

        logger.info("Generated $generatedPackage.$clientClassName for $containerName")
    }

    // -------------------------------------------------------------------------
    // 6️⃣  Transform a single KSFunctionDeclaration into a KotlinPoet FunSpec
    // -------------------------------------------------------------------------
    private fun generateFunctionStub(fn: KSFunctionDeclaration): FunSpec? {
        val endpointConfig = extractEndpointConfiguration(fn) ?: return null
        val parameterAnalysis = analyzeParameters(fn)

        return buildFunctionSpec(fn, endpointConfig, parameterAnalysis)
    }

    private data class EndpointConfiguration(
        val httpMethod: String,
        val rawPath: String,
    )

    private fun extractEndpointConfiguration(fn: KSFunctionDeclaration): EndpointConfiguration? {
        val endpointAnno =
            fn.annotations.firstOrNull {
                it.shortName.asString() == "RestEndpoint"
            } ?: return null

        val methodArg = endpointAnno.arguments.first { it.name?.asString() == "method" }
        val pathArg = endpointAnno.arguments.first { it.name?.asString() == "path" }

        val httpMethod = (methodArg.value as KSName).getShortName()
        val rawPath = pathArg.value as String

        return EndpointConfiguration(httpMethod, rawPath)
    }

    private data class ParameterAnalysis(
        val pathParams: List<KSValueParameter>,
        val queryParams: List<Pair<String, KSValueParameter>>,
        val bodyParam: KSValueParameter?,
    )

    private fun analyzeParameters(fn: KSFunctionDeclaration): ParameterAnalysis {
        val queryParams = mutableListOf<Pair<String, KSValueParameter>>()
        val pathParams = mutableListOf<KSValueParameter>()
        var bodyParam: KSValueParameter? = null

        fn.parameters.forEach { param ->
            when {
                param.hasAnnotation("QueryParam") -> {
                    val qpAnno = param.annotations.first { it.shortName.asString() == "QueryParam" }
                    val nameArg = qpAnno.arguments.first { it.name?.asString() == "name" }.value as String
                    queryParams.add(nameArg to param)
                }

                param.hasAnnotation("Body") -> {
                    bodyParam = param
                }

                else -> {
                    pathParams.add(param)
                }
            }
        }

        return ParameterAnalysis(pathParams, queryParams, bodyParam)
    }

    private fun KSValueParameter.hasAnnotation(name: String): Boolean =
        annotations.any { it.shortName.asString() == name }

    private fun buildFunctionSpec(
        fn: KSFunctionDeclaration,
        config: EndpointConfiguration,
        params: ParameterAnalysis,
    ): FunSpec {
        val funBuilder =
            FunSpec
                .builder(fn.simpleName.asString())
                .addModifiers(KModifier.SUSPEND)

        fn.returnType?.let { ret ->
            funBuilder.returns(ret.toTypeName())
        }

        fn.parameters.forEach { param ->
            funBuilder.addParameter(
                param.name?.asString() ?: "param",
                param.type.toTypeName(),
            )
        }

        val urlExpression = buildUrlExpression(config.rawPath, params)
        val httpCall = buildHttpCall(config.httpMethod, urlExpression, params.bodyParam)

        funBuilder.addStatement("return $httpCall")
        return funBuilder.build()
    }

    private fun buildUrlExpression(
        rawPath: String,
        params: ParameterAnalysis,
    ): String {
        var urlExpression = "\"$rawPath\""

        // Replace path variable placeholders
        params.pathParams.forEach { param ->
            val placeholder = "{${param.name?.asString()}}"
            urlExpression = urlExpression.replace(placeholder, "\${${param.name?.asString()}}")
        }

        // Append query parameters
        if (params.queryParams.isNotEmpty()) {
            val qpString =
                params.queryParams.joinToString("&") { (name, param) ->
                    "$name=\${${param.name?.asString()}}"
                }
            urlExpression = "\"${'$'}{$urlExpression}?$qpString\""
        }

        return urlExpression
    }

    private fun buildHttpCall(
        httpMethod: String,
        urlExpression: String,
        bodyParam: KSValueParameter?,
    ): String? =
        when (httpMethod) {
            "GET" -> {
                "http.get($urlExpression)"
            }

            "DELETE" -> {
                "http.delete($urlExpression)"
            }

            "POST", "PUT", "PATCH" -> {
                val bodyExpr = bodyParam?.let { "${it.name?.asString()}" } ?: "null"
                "http.${httpMethod.lowercase()}($urlExpression, $bodyExpr)"
            }

            else -> {
                logger.error("Unsupported HTTP method $httpMethod")
                null
            }
        }
}
