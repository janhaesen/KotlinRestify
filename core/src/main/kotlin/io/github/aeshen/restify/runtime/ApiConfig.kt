package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.retry.RetryPolicy

/**
 * Configuration for API runtime.
 *
 * - `baseUrl` is required in the primary constructor and is immutable.
 * - Use `ApiConfig.builder(baseUrl)` to construct with a fluent Builder.
 * - `toBuilder()` lets you start from an existing config to modify fields.
 */
data class ApiConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val timeoutMillis: Long? = null,
    val retryPolicy: RetryPolicy? = null,
    val followRedirects: Boolean = true,
) {
    companion object {
        @JvmStatic
        fun builder(baseUrl: String): Builder = Builder(baseUrl)

        @JvmStatic
        inline fun build(
            baseUrl: String,
            block: Builder.() -> Unit,
        ): ApiConfig = builder(baseUrl).apply(block).build()
    }

    class Builder internal constructor(
        private val baseUrl: String,
    ) {
        var defaultHeaders: Map<String, String> = emptyMap()
        var timeoutMillis: Long? = null
        var retryPolicy: RetryPolicy? = null
        var followRedirects: Boolean = true

        fun defaultHeaders(headers: Map<String, String>) = apply { this.defaultHeaders = headers }

        fun timeoutMillis(ms: Long?) = apply { this.timeoutMillis = ms }

        fun retryPolicy(policy: RetryPolicy?) = apply { this.retryPolicy = policy }

        fun followRedirects(follow: Boolean) = apply { this.followRedirects = follow }

        fun build(): ApiConfig =
            ApiConfig(
                baseUrl = baseUrl,
                defaultHeaders = defaultHeaders,
                timeoutMillis = timeoutMillis,
                retryPolicy = retryPolicy,
                followRedirects = followRedirects,
            )
    }
}

/**
 * Helpers to merge ApiConfig instances in a single place.
 *
 * - `baseUrl` from override is used only when non-blank.
 * - `defaultHeaders` are combined: base first, then override (override wins on same keys).
 * - other optional fields prefer override when present, otherwise keep base.
 */
internal fun ApiConfig.mergeWith(override: ApiConfig?): ApiConfig {
    if (override == null) {
        return this
    }

    return this.copy(
        baseUrl = override.baseUrl.ifBlank { this.baseUrl },
        defaultHeaders = this.defaultHeaders + override.defaultHeaders,
        timeoutMillis = override.timeoutMillis ?: this.timeoutMillis,
        retryPolicy = override.retryPolicy ?: retryPolicy,
        followRedirects = override.followRedirects,
    )
}
