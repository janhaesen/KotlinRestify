package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.aeshen.restify.processor.generator.CallGenerator
import io.github.aeshen.restify.processor.generator.RUNTIME_PACKAGE
import io.github.aeshen.restify.processor.generator.collectionRawTypes

class ClientGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val generatedPackage: String,
) {
    companion object {
        private val WITH_CONTEXT = MemberName("kotlinx.coroutines", "withContext")
    }

    fun generate(
        containerName: String,
        endpoints: List<EndpointAnalyzer.Endpoint>,
    ) {
        val clientClassName = "${containerName.substringAfterLast('.')}Client"
        val fileBuilder = FileSpec.builder(generatedPackage, clientClassName)

        // Import TypeKey when any endpoint uses a non-Unit return type (the generator will emit TypeKey instances)
        val needsTypeKeyImport =
            endpoints.any { ep ->
                val rt = safeToTypeName(ep.function.returnType)
                rt != ClassName("kotlin", "Unit")
            }
        if (needsTypeKeyImport) {
            fileBuilder.addImport("$RUNTIME_PACKAGE.client.body", "TypeKey")
        }

        val callerParam =
            ParameterSpec
                .builder(
                    "caller",
                    ClassName(RUNTIME_PACKAGE, "ApiCaller"),
                ).build()

        val mapperFactoryParam =
            ParameterSpec
                .builder(
                    "mapperFactory",
                    ClassName(
                        "$RUNTIME_PACKAGE.client.body",
                        "ResponseMapperFactory",
                    ),
                ).build()

        val dispatcherParam =
            ParameterSpec
                .builder(
                    "dispatcher",
                    ClassName("kotlinx.coroutines", "CoroutineDispatcher"),
                ).defaultValue("%T.IO", ClassName("kotlinx.coroutines", "Dispatchers"))
                .build()

        val clientClassBuilder =
            createClientClass(
                clientClassName = clientClassName,
                callerParam = callerParam,
                mapperFactoryParam = mapperFactoryParam,
                dispatcherParam = dispatcherParam,
                containerName = containerName,
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

    private fun createClientClass(
        clientClassName: String,
        callerParam: ParameterSpec,
        mapperFactoryParam: ParameterSpec,
        dispatcherParam: ParameterSpec,
        containerName: String,
    ): TypeSpec.Builder {
        val clientClassBuilder =
            TypeSpec
                .classBuilder(clientClassName)
                .addAnnotation(
                    AnnotationSpec
                        .builder(ClassName("javax.annotation.processing", "Generated"))
                        .addMember("%S", "KotlinRestifyProcessor")
                        .build(),
                ).primaryConstructor(
                    FunSpec
                        .constructorBuilder()
                        .addParameter(callerParam)
                        .addParameter(mapperFactoryParam)
                        .addParameter(dispatcherParam)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("caller", callerParam.type)
                        .initializer("caller")
                        .addModifiers(KModifier.PRIVATE)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("mapperFactory", mapperFactoryParam.type)
                        .initializer("mapperFactory")
                        .addModifiers(KModifier.PRIVATE)
                        .build(),
                ).addProperty(
                    PropertySpec
                        .builder("dispatcher", dispatcherParam.type)
                        .initializer("dispatcher")
                        .addModifiers(KModifier.PRIVATE)
                        .build(),
                ).addSuperinterface(ClassName.bestGuess(containerName))
        return clientClassBuilder
    }

    private fun buildFunction(endpoint: EndpointAnalyzer.Endpoint): FunSpec {
        val fn = endpoint.function
        val returnTypeName = safeToTypeName(fn.returnType)

        val funBuilder =
            FunSpec
                .builder(fn.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                .returns(returnTypeName)

        // parameters
        fn.parameters.forEach { param ->
            val paramType = safeToTypeName(param.type)
            funBuilder.addParameter(param.name?.asString() ?: "param", paramType)
        }

        // withContext wrapper start — use MemberName so import is generated
        funBuilder.addCode("return %M(dispatcher) {\n", WITH_CONTEXT)

        // delegate to CallGenerator which now emits path/query/request/mapper inline
        funBuilder.addCode(CallGenerator.generate(endpoint, returnTypeName, fn.parameters))

        // close withContext
        funBuilder.addCode("}\n")
        return funBuilder.build()
    }

    private fun writeFile(
        fileSpec: FileSpec,
        sourceFiles: List<KSFile>,
    ) {
        val srcs = sourceFiles.toTypedArray()
        val deps = Dependencies(aggregating = true, *srcs)

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

    private fun safeToTypeName(typeRef: KSTypeReference?): TypeName {
        if (typeRef == null) {
            return ClassName("kotlin", "Unit")
        }

        return try {
            typeRef.toTypeName()
        } catch (_: IllegalArgumentException) {
            val resolved =
                try {
                    typeRef.resolve()
                } catch (_: Exception) {
                    null
                }

            val fqn = resolved?.declaration?.qualifiedName?.asString()
            val base =
                if (!fqn.isNullOrBlank()) {
                    ClassName.bestGuess(
                        fqn,
                    )
                } else {
                    ClassName("kotlin", "Any")
                }

            if (resolved?.nullability == Nullability.NULLABLE) {
                base.copy(nullable = true)
            } else {
                base
            }
        }
    }
}
