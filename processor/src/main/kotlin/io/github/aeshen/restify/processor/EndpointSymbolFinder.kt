package io.github.aeshen.restify.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class EndpointSymbolFinder(
    private val resolvedTypes: AnnotationTypeResolver.ResolvedAnnotationTypes,
) {
    fun find(resolver: Resolver): Map<String, List<KSFunctionDeclaration>> {
        val result = mutableMapOf<String, MutableList<KSFunctionDeclaration>>()
        val seenSignatures = mutableSetOf<String>()

        val methodDecls =
            resolvedTypes.httpMethodAnnos.values
                .map { it.declaration }
                .toSet()

        findClassLevelEndpoints(resolver, methodDecls, result, seenSignatures)
        findTopLevelEndpoints(resolver, result, seenSignatures)

        return result
    }

    private fun findClassLevelEndpoints(
        resolver: Resolver,
        methodDecls: Set<com.google.devtools.ksp.symbol.KSDeclaration>,
        result: MutableMap<String, MutableList<KSFunctionDeclaration>>,
        seenSignatures: MutableSet<String>,
    ) {
        val resourceFqn =
            resolvedTypes.restEndpoint.declaration.qualifiedName
                ?.asString()
                ?: return

        resolver
            .getSymbolsWithAnnotation(resourceFqn)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { cls ->
                val functions =
                    cls
                        .getDeclaredFunctions()
                        .filter { fn ->
                            fn.annotations.any { ann ->
                                methodDecls.contains(ann.annotationType.resolve().declaration)
                            }
                        }.toList()

                if (functions.isNotEmpty()) {
                    val key = cls.qualifiedName?.asString() ?: "<anonymous>"
                    val list = result.getOrPut(key) { mutableListOf() }
                    functions.forEach { fn -> addIfNew(list, seenSignatures, fn) }
                }
            }
    }

    private fun findTopLevelEndpoints(
        resolver: Resolver,
        result: MutableMap<String, MutableList<KSFunctionDeclaration>>,
        seenSignatures: MutableSet<String>,
    ) {
        resolvedTypes.httpMethodAnnos.values.forEach { httpType ->
            val httpFqn = httpType.declaration.qualifiedName?.asString() ?: return@forEach
            resolver
                .getSymbolsWithAnnotation(httpFqn)
                .filterIsInstance<KSFunctionDeclaration>()
                .forEach { fn ->
                    val parent = fn.parentDeclaration?.qualifiedName?.asString() ?: "<top-level>"
                    val list = result.getOrPut(parent) { mutableListOf() }
                    addIfNew(list, seenSignatures, fn)
                }
        }
    }

    private fun addIfNew(
        list: MutableList<KSFunctionDeclaration>,
        seenSignatures: MutableSet<String>,
        fn: KSFunctionDeclaration,
    ) {
        val sig = signature(fn)
        if (seenSignatures.add(sig)) {
            list.add(fn)
        }
    }

    private fun signature(fn: KSFunctionDeclaration): String {
        val parent = fn.parentDeclaration?.qualifiedName?.asString() ?: "<top-level>"
        val params =
            fn.parameters.joinToString(",") { p ->
                p.type
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() ?: p.type.resolve().toString()
            }
        return "$parent#${fn.simpleName.asString()}($params)"
    }
}
