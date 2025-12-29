package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

class EndpointSymbolFinder(
    private val restEndpointType: KSType,
) {
    fun find(resolver: Resolver): Map<String, List<KSFunctionDeclaration>> =
        resolver
            .getSymbolsWithAnnotation(
                restEndpointType.declaration.qualifiedName!!.asString(),
            ).filterIsInstance<KSFunctionDeclaration>()
            .groupBy {
                it.parentDeclaration?.qualifiedName?.asString() ?: "<top-level>"
            }
}
