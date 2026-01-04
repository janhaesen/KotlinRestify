package io.github.aeshen.restify.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import io.github.aeshen.restify.annotation.http.HttpDelete
import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.HttpMethod
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

    private val httpMethodAnnosInternal: MutableMap<HttpMethod, KSType> = mutableMapOf()

    fun init(resolver: Resolver) {
        if (restEndpointInternal != null) {
            return
        }

        // resolve Resource (canonical)
        restEndpointInternal =
            resolver
                .getClassDeclarationByName(
                    resolver.getKSNameFromString(Resource::class.qualifiedName ?: return),
                )
                ?.asStarProjectedType()

        val restDecl = restEndpointInternal?.declaration
        val resolvedPackage = restDecl?.packageName?.asString().orEmpty()
        val annotationRoot = determineAnnotationRoot(resolvedPackage)

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

        listOf(
            HttpMethod.GET to listOf(HttpGet::class.qualifiedName),
            HttpMethod.POST to listOf(HttpPost::class.qualifiedName),
            HttpMethod.PUT to listOf(HttpPut::class.qualifiedName),
            HttpMethod.DELETE to listOf(HttpDelete::class.qualifiedName),
            HttpMethod.PATCH to listOf(HttpPatch::class.qualifiedName),
        ).forEach { (key, directCandidates) ->
            val extra =
                annotationRoot.ifBlank { null }?.let {
                    listOf(
                        "$it.http.Http${key.name.replaceFirstChar { c ->
                            c.uppercase(Locale.ROOT)
                        }}",
                    )
                } ?: emptyList()
            val resolved =
                resolveAny(
                    resolver,
                    *(directCandidates.filterNotNull().toTypedArray()),
                    *extra.toTypedArray(),
                )
            if (resolved != null) {
                httpMethodAnnosInternal[key] = resolved
            }
        }
    }

    private fun determineAnnotationRoot(resolvedPackage: String): String =
        when {
            resolvedPackage.endsWith(".http") -> resolvedPackage.removeSuffix(".http")

            resolvedPackage.endsWith(
                ".annotation.http",
            ) -> resolvedPackage.removeSuffix(".http")

            else -> resolvedPackage
        }

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
            val decl =
                resolver.getClassDeclarationByName(name)
                    ?: continue
            return decl.asStarProjectedType()
        }
        return null
    }

    data class ResolvedAnnotationTypes(
        val restEndpoint: KSType,
        val bodyAnno: KSType?,
        val queryAnno: KSType?,
        val pathAnno: KSType?,
        val httpMethodAnnos: Map<HttpMethod, KSType>,
    )

    fun requireResolvedTypes(logger: KSPLogger): ResolvedAnnotationTypes? {
        if (restEndpointInternal == null) {
            logger.error(
                "Required Resource/RestEndpoint annotation not found on processor" +
                    " classpath; ensure your annotations module is available.",
            )
            return null
        }

        if (httpMethodAnnosInternal.isEmpty()) {
            logger.error(
                "No HTTP method annotations (e.g. @HttpGet/@HttpPost) were resolved;" +
                    " ensure your annotations module is available and on the processor classpath.",
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
