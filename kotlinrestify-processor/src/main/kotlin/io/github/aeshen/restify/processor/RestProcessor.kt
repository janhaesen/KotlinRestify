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
    private val env: SymbolProcessorEnvironment,
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
        // Initialise the annotation KSType references (only once)
        initTypes(resolver)

        // Guard against a broken setup – if any of the annotation types are missing,
        // we cannot continue.
        if (restEndpointType == null) {
            logger.error("Unable to locate RestEndpoint annotation. Did you forget to add the annotations module?")
            return emptyList()
        }

        // -----------------------------------------------------------------
        // 3️⃣  Find every function annotated with @RestEndpoint
        // -----------------------------------------------------------------
        val endpointFunctions =
            resolver
                .getSymbolsWithAnnotation(
                    restEndpointType!!.declaration.qualifiedName!!.asString(),
                ).filterIsInstance<KSFunctionDeclaration>()

        if (!endpointFunctions.iterator().hasNext()) {
            // Nothing to generate – return empty list to indicate we’re done.
            return emptyList()
        }

        // -----------------------------------------------------------------
        // 4️⃣  Group by the containing class / file (so we generate one client per group)
        // -----------------------------------------------------------------
        val groups =
            endpointFunctions.groupBy { fn ->
                fn.parentDeclaration?.qualifiedName?.asString() ?: "<top‑level>"
            }

        groups.forEach { (containerName, functions) ->
            generateClient(containerName, functions, resolver)
        }

        // Returning an empty list tells KSP we have processed everything.
        return emptyList()
    }

    // -------------------------------------------------------------------------
    // 5️⃣  Code‑generation helper – creates a thin client wrapper for a group.
    // -------------------------------------------------------------------------
    private fun generateClient(
        containerName: String,
        functions: List<KSFunctionDeclaration>,
        resolver: Resolver,
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
            generateFunctionStub(fn, resolver)?.let { clientClass.addFunction(it) }
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
    private fun generateFunctionStub(
        fn: KSFunctionDeclaration,
        resolver: Resolver,
    ): FunSpec? {
        // -----------------------------------------------------------------
        // 6.1  Grab the @RestEndpoint annotation and its arguments
        // -----------------------------------------------------------------
        val endpointAnno =
            fn.annotations.firstOrNull {
                it.shortName.asString() == "RestEndpoint"
            } ?: return null

        // Extract `method` (enum) and `path` (String) from the annotation
        val methodArg = endpointAnno.arguments.first { it.name?.asString() == "method" }
        val pathArg = endpointAnno.arguments.first { it.name?.asString() == "path" }

        // `method` is stored as a KSName that points to the enum constant (e.g. GET)
        val httpMethod = (methodArg.value as KSName).getShortName()
        val rawPath = pathArg.value as String

        // -----------------------------------------------------------------
        // 6.2  Prepare the function signature (copy parameters & return type)
        // -----------------------------------------------------------------
        val funBuilder =
            FunSpec
                .builder(fn.simpleName.asString())
                .addModifiers(KModifier.SUSPEND)

        // Return type (if any)
        fn.returnType?.let { ret ->
            funBuilder.returns(ret.toTypeName())
        }

        // Copy all parameters (we’ll later decide which are path/query/body)
        fn.parameters.forEach { param ->
            funBuilder.addParameter(
                param.name?.asString() ?: "param",
                param.type.toTypeName(),
            )
        }

        // -----------------------------------------------------------------
        // 6.3  Analyse parameters: path vars, @QueryParam, @Body
        // -----------------------------------------------------------------
        val queryParams = mutableListOf<Pair<String, KSValueParameter>>()
        var bodyParam: KSValueParameter? = null

        fn.parameters.forEach { param ->
            when {
                param.annotations.any { it.shortName.asString() == "QueryParam" } -> {
                    val qpAnno = param.annotations.first { it.shortName.asString() == "QueryParam" }
                    val nameArg = qpAnno.arguments.first { it.name?.asString() == "name" }.value as String
                    queryParams.add(nameArg to param)
                }

                param.annotations.any { it.shortName.asString() == "Body" } -> {
                    bodyParam = param
                }

                else -> {
                    // Unannotated parameters are assumed to be **path variables**
                    // (e.g. function foo(id: String) -> path "/users/{id}")
                }
            }
        }

        // -----------------------------------------------------------------
        // 6.4  Build the request URL (replace `{var}` placeholders + query string)
        // -----------------------------------------------------------------
        var urlExpression = "\"$rawPath\""

        // Replace `{name}` placeholders with Kotlin string interpolation
        fn.parameters
            .filter { p ->
                p.annotations.none { it.shortName.asString() in listOf("QueryParam", "Body") }
            }.forEach { p ->
                val placeholder = "{${p.name?.asString()}}"
                urlExpression = urlExpression.replace(placeholder, "\${${p.name?.asString()}}")
            }

        // Append query parameters if any
        if (queryParams.isNotEmpty()) {
            val qpString =
                queryParams.joinToString("&") { (name, param) ->
                    "$name=\${${param.name?.asString()}}"
                }
            urlExpression = "\"${'$'}{$urlExpression}?$qpString\""
        }

        // -----------------------------------------------------------------
        // 6.5  Emit the call to the injected HttpClient
        // -----------------------------------------------------------------
        val httpCall =
            when (httpMethod) {
                "GET" -> {
                    "http.get($urlExpression)"
                }

                "POST" -> {
                    val bodyExpr = bodyParam?.let { "${it.name?.asString()}" } ?: "null"
                    "http.post($urlExpression, $bodyExpr)"
                }

                "PUT" -> {
                    val bodyExpr = bodyParam?.let { "${it.name?.asString()}" } ?: "null"
                    "http.put($urlExpression, $bodyExpr)"
                }

                "PATCH" -> {
                    val bodyExpr = bodyParam?.let { "${it.name?.asString()}" } ?: "null"
                    "http.patch($urlExpression, $bodyExpr)"
                }

                "DELETE" -> {
                    "http.delete($urlExpression)"
                }

                else -> {
                    logger.error("Unsupported HTTP method $httpMethod on ${fn.qualifiedName?.asString()}")
                    return null
                }
            }

        // -----------------------------------------------------------------
        // 6.6  Return the result (the generated function mirrors the original return type)
        // -----------------------------------------------------------------
        funBuilder.addStatement("return $httpCall")
        return funBuilder.build()
    }
}
