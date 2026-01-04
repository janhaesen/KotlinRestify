package io.github.aeshen.restify.runtime.client

import io.github.aeshen.restify.runtime.ApiConfig
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.adapter.HttpClientAdapter
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.client.body.serializer.impl.DefaultBodySerializer
import io.github.aeshen.restify.runtime.client.path.UrlBuilder
import io.github.aeshen.restify.runtime.client.path.impl.DefaultUrlBuilder
import io.github.aeshen.restify.runtime.mergeWith
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
 * - `closeAdapter` as an idempotent, thread-safe close for the underlying low-level adapter
 */
internal class AdapterHttpClient(
    private val adapter: HttpClientAdapter,
    private val baseConfig: ApiConfig,
    private val urlBuilder: UrlBuilder = DefaultUrlBuilder,
) {
    private val closed = AtomicBoolean(false)

    /**
     * Accept an optional mapper so the transport can prefer the mapper's BodySerializer when
     * serializing the outgoing request body.
     */
    suspend fun execute(
        request: RequestData,
        mapper: ResponseMapper<*>,
    ): ResponseData {
        // Merge configuration first (so UrlBuilder can receive effective baseUrl)
        val cfg = baseConfig.mergeWith(request.perRequestConfig)

        // Transport resolves the final URL from template + path params + nullable query params
        val fullUrl =
            urlBuilder.build(
                cfg.baseUrl,
                request.urlPath,
                request.pathParameters,
                request.queryParameters,
            )

        val serialized =
            mapper
                .bodySerializer
                .serialize(request.body, request.contentType)

        // Merge config default headers first, then request headers override
        val headers = cfg.defaultHeaders.toMutableMap().apply { putAll(request.headers) }

        // Set Content-Type when serializer provided a content type (unless caller already set it)
        if (serialized.contentType != null && headers["Content-Type"] == null) {
            headers["Content-Type"] = serialized.contentType
        }

        // Create a request copy for the adapter with serialized body & final headers; query
        // params no longer needed downstream
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

    fun effectiveRetryPolicyFor(request: RequestData?): RetryPolicy? =
        baseConfig
            .mergeWith(request?.perRequestConfig)
            .retryPolicy

    internal fun toApiConfig(): ApiConfig = baseConfig

    fun closeAdapter() {
        if (closed.compareAndSet(false, true)) {
            try {
                adapter.close()
            } catch (_: Throwable) {
            }
        }
    }
}
