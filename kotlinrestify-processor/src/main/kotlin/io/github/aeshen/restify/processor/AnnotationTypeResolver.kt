package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType

class AnnotationTypeResolver {
    var restEndpoint: KSType? = null
        private set

    fun init(resolver: Resolver) {
        if (restEndpoint != null) return

        restEndpoint =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(
                        "io.github.aeshen.restify.annotation.RestEndpoint",
                    ),
                )?.asStarProjectedType()
    }

    fun isValid(logger: KSPLogger): Boolean {
        if (restEndpoint == null) {
            logger.error("Unable to locate RestEndpoint annotation.")
            return false
        }
        return true
    }
}
