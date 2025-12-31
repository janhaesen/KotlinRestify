package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import io.github.aeshen.restify.annotation.http.HttpDelete
import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.HttpPatch
import io.github.aeshen.restify.annotation.http.HttpPost
import io.github.aeshen.restify.annotation.http.HttpPut
import io.github.aeshen.restify.annotation.http.Resource
import io.github.aeshen.restify.annotation.param.Body
import io.github.aeshen.restify.annotation.param.Path
import io.github.aeshen.restify.annotation.param.Query
import java.util.Locale

class AnnotationTypeResolver {
    var restEndpoint: KSType? = null
        private set

    // additional resolved annotation types (may be null if not found)
    var bodyAnno: KSType? = null
        private set
    var queryAnno: KSType? = null
        private set
    var pathAnno: KSType? = null
        private set

    // common HTTP method annotations (GET, POST, PUT, DELETE, PATCH) if you have them declared
    val httpMethodAnnos: MutableMap<String, KSType> = mutableMapOf()

    /**
     * Initialize resolution. Prefer direct annotation class qualified names when available.
     * Only if a direct reference is unavailable do we try a minimal annotationRoot-derived fallback.
     */
    fun init(resolver: Resolver) {
        if (restEndpoint != null) {
            return
        }

        // resolve RestEndpoint (single canonical location)
        restEndpoint =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(Resource::class.qualifiedName ?: return),
                )?.asStarProjectedType()

        // derive the annotation package root from the resolved RestEndpoint if available
        val restDecl = restEndpoint?.declaration
        val resolvedPackage = restDecl?.packageName?.asString().orEmpty()
        val annotationRoot =
            when {
                resolvedPackage.endsWith(".http") -> resolvedPackage.removeSuffix(".http")
                resolvedPackage.endsWith(".annotation.http") -> resolvedPackage.removeSuffix(".http")
                else -> resolvedPackage
            }

        // Prefer direct class qualified names; if missing, try just a single annotationRoot variant.
        bodyAnno =
            resolveAny(
                resolver,
                Body::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Body" },
            )

        queryAnno =
            resolveAny(
                resolver,
                Query::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Query" },
            )

        pathAnno =
            resolveAny(
                resolver,
                Path::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Path" },
            )

        // HTTP method annotations — prefer direct class names, minimal fallback to annotationRoot.http
        listOf(
            "GET" to listOf(HttpGet::class.qualifiedName),
            "POST" to listOf(HttpPost::class.qualifiedName),
            "PUT" to listOf(HttpPut::class.qualifiedName),
            "DELETE" to listOf(HttpDelete::class.qualifiedName),
            "PATCH" to listOf(HttpPatch::class.qualifiedName),
        ).forEach { (key, directCandidates) ->
            val extra =
                annotationRoot
                    .ifBlank { null }
                    ?.let { listOf("$it.http.Http${key.replaceFirstChar { firstChar -> firstChar.uppercase(Locale.getDefault()) }}") }
                    ?: emptyList()
            val resolved =
                resolveAny(resolver, *(directCandidates.filterNotNull().toTypedArray()), *extra.toTypedArray())
            if (resolved != null) {
                httpMethodAnnos[key] = resolved
            }
        }
    }

    // Helper to try a list of candidate FQNs and return first found KSType (filters out nulls)
    private fun resolveAny(
        resolver: Resolver,
        vararg candidatesNullable: String?,
    ): KSType? {
        val candidates = candidatesNullable.filterNotNull()
        candidates.forEach { fqn ->
            val name =
                try {
                    resolver.getKSNameFromString(fqn)
                } catch (_: Exception) {
                    null
                } ?: return@forEach
            val decl = resolver.getClassDeclarationByName(name)
            if (decl != null) {
                return decl.asStarProjectedType()
            }
        }
        return null
    }

    /**
     * Quick validity check used by the processor to fail early when the annotations module
     * isn't available on the annotation processor classpath.
     */
    fun isValid(logger: KSPLogger): Boolean {
        if (restEndpoint == null) {
            logger.error(
                "RestEndpoint annotation type not found. Ensure the annotations module is on the processor classpath.",
            )
            return false
        }
        return true
    }
}
