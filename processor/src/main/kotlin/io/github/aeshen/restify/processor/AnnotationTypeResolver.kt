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
    // keep internals private; expose a typed view via requireResolvedTypes(...)
    private var restEndpointInternal: KSType? = null

    private var bodyAnnoInternal: KSType? = null
    private var queryAnnoInternal: KSType? = null
    private var pathAnnoInternal: KSType? = null

    private val httpMethodAnnosInternal: MutableMap<String, KSType> = mutableMapOf()

    /**
     * Initialize resolution. Idempotent. Prefer direct class references and only fall back
     * to a single annotation-root variant when necessary.
     */
    fun init(resolver: Resolver) {
        if (restEndpointInternal != null) {
            return
        }

        // resolve Resource (canonical)
        restEndpointInternal =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(Resource::class.qualifiedName ?: return),
                )?.asStarProjectedType()

        val restDecl = restEndpointInternal?.declaration
        val resolvedPackage = restDecl?.packageName?.asString().orEmpty()
        val annotationRoot =
            when {
                resolvedPackage.endsWith(".http") -> resolvedPackage.removeSuffix(".http")

                resolvedPackage.endsWith(
                    ".annotation.http",
                ) -> resolvedPackage.removeSuffix(".http")

                else -> resolvedPackage
            }

        // prefer direct class qnames, fall back to a single derived candidate if needed
        bodyAnnoInternal =
            resolveAny(
                resolver,
                Body::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Body" },
            )

        queryAnnoInternal =
            resolveAny(
                resolver,
                Query::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Query" },
            )

        pathAnnoInternal =
            resolveAny(
                resolver,
                Path::class.qualifiedName,
                annotationRoot.ifBlank { null }?.let { "$it.param.Path" },
            )

        // HTTP methods: prefer direct, minimal fallback
        listOf(
            "GET" to listOf(HttpGet::class.qualifiedName),
            "POST" to listOf(HttpPost::class.qualifiedName),
            "PUT" to listOf(HttpPut::class.qualifiedName),
            "DELETE" to listOf(HttpDelete::class.qualifiedName),
            "PATCH" to listOf(HttpPatch::class.qualifiedName),
        ).forEach { (key, directCandidates) ->
            val extra =
                annotationRoot.ifBlank { null }?.let {
                    listOf(
                        "$it.http.Http${key.replaceFirstChar { c ->
                            c.uppercase(Locale.getDefault())
                        }}",
                    )
                } ?: emptyList()
            val resolved =
                resolveAny(
                    resolver,
                    *(directCandidates.filterNotNull().toTypedArray()),
                    *extra.toTypedArray(),
                )
            if (resolved != null) httpMethodAnnosInternal[key] = resolved
        }
    }

    // Helper: try candidates in order, return first resolved KSType
    private fun resolveAny(
        resolver: Resolver,
        vararg candidatesNullable: String?,
    ): KSType? {
        val candidates = candidatesNullable.filterNotNull()
        for (fqn in candidates) {
            val name =
                try {
                    resolver.getKSNameFromString(fqn)
                } catch (_: Exception) {
                    null
                } ?: continue
            val decl = resolver.getClassDeclarationByName(name) ?: continue
            return decl.asStarProjectedType()
        }
        return null
    }

    /**
     * Typed, non-nullable view of the resolved annotation types.
     * Consumers should call requireResolvedTypes(logger) after init(...) to obtain this view.
     */
    data class ResolvedAnnotationTypes(
        val restEndpoint: KSType,
        val bodyAnno: KSType?,
        val queryAnno: KSType?,
        val pathAnno: KSType?,
        val httpMethodAnnos: Map<String, KSType>,
    )

    /**
     * Return a typed ResolvedAnnotationTypes view or null (and log an error) if essential annotations
     * are missing.
     */
    fun requireResolvedTypes(logger: KSPLogger): ResolvedAnnotationTypes? {
        if (restEndpointInternal == null) {
            logger.error(
                "Required Resource/RestEndpoint annotation not found on processor classpath; ensure your annotations module is available.",
            )
            return null
        }
        return ResolvedAnnotationTypes(
            restEndpoint = restEndpointInternal!!,
            bodyAnno = bodyAnnoInternal,
            queryAnno = queryAnnoInternal,
            pathAnno = pathAnnoInternal,
            httpMethodAnnos = httpMethodAnnosInternal.toMap(),
        )
    }
}
