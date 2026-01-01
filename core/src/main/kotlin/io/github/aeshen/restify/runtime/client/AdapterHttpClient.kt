package io.github.aeshen.restify.runtime.client

import io.github.aeshen.restify.runtime.ApiConfig
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.adapter.HttpClientAdapter
import io.github.aeshen.restify.runtime.client.body.BodySerializer
import io.github.aeshen.restify.runtime.client.body.DefaultBodySerializer
import io.github.aeshen.restify.runtime.client.path.UrlBuilder
import io.github.aeshen.restify.runtime.client.path.impl.DefaultUrlBuilder
import io.github.aeshen.restify.runtime.retry.RetryPolicy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdapterHttpClient: bridges the runtime-level HttpClient interface to a low-level HttpClientAdapter.
 *
 * - Merges base config + per-request config
 * - Serializes request body using the configured BodySerializer (or DefaultBodySerializer)
 * - Ensures Content-Type header is set when serializer provides one
 * - Delegates execution to the provided HttpClientAdapter
 *
 * Adds:
 * - `effectiveRetryPolicyFor` to obtain only the retry policy (narrower surface than exposing
 *  baseConfig)
 * - `closeAdapter` as an idempotent, thread-safe close for the underlying low-level adapter
 */
internal class AdapterHttpClient(
    private val adapter: HttpClientAdapter,
    internal val baseConfig: ApiConfig,
    private val urlBuilder: UrlBuilder = DefaultUrlBuilder,
) {
    private val closed = AtomicBoolean(false)

    suspend fun execute(request: RequestData): ResponseData {
        // Merge configuration first (so UrlBuilder can receive effective baseUrl)
        val cfg = mergeConfig(baseConfig, request.perRequestConfig)
        val serializer: BodySerializer = cfg.bodySerializer ?: DefaultBodySerializer

        // Transport resolves the final URL from template + path params + nullable query params
        val fullUrl =
            urlBuilder.build(
                cfg.baseUrl,
                request.urlPath,
                request.pathParameters,
                request.queryParameters,
            )

        val serialized = serializer.serialize(request.body, request.contentType)

        // Merge headers and set Content-Type when serializer provided a content type (unless
        // caller already set it)
        val headers = request.headers.toMutableMap()
        if (serialized.contentType != null && headers["Content-Type"] == null) {
            headers["Content-Type"] = serialized.contentType
        }

        // Create a request copy for the adapter with serialized body & headers; query params no
        // longer needed downstream
        val adapterRequest =
            request.copy(
                urlPath = fullUrl,
                pathParameters = emptyMap(),
                queryParameters = emptyMap(),
                body = serialized.payload,
                headers = headers,
                contentType = null,
                perRequestConfig = null,
            )

        return adapter.execute(adapterRequest, cfg)
    }

    fun effectiveRetryPolicyFor(request: RequestData?): RetryPolicy? {
        val cfg = mergeConfig(baseConfig, request?.perRequestConfig)
        return cfg.retryPolicy
    }

    fun closeAdapter() {
        if (closed.compareAndSet(false, true)) {
            try {
                adapter.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun mergeConfig(
        base: ApiConfig,
        override: ApiConfig?,
    ): ApiConfig {
        if (override == null) {
            return base
        }
        return base.copy(
            baseUrl =
                override.baseUrl.ifBlank {
                    base.baseUrl
                },
            defaultHeaders = base.defaultHeaders + override.defaultHeaders,
            timeoutMillis = override.timeoutMillis ?: base.timeoutMillis,
            bodySerializer = override.bodySerializer ?: base.bodySerializer,
            retryPolicy = override.retryPolicy ?: base.retryPolicy,
            followRedirects = override.followRedirects,
        )
    }
}
