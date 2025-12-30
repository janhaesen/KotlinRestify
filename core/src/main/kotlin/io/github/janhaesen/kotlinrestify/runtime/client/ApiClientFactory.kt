package io.github.janhaesen.kotlinrestify.runtime.client

import io.github.janhaesen.kotlinrestify.runtime.ApiConfig
import io.github.janhaesen.kotlinrestify.runtime.GeneratedApiCaller
import io.github.janhaesen.kotlinrestify.runtime.TimeBoundRetryPolicy
import io.github.janhaesen.kotlinrestify.runtime.client.ktor.KtorGeneratedApiCaller
import io.github.janhaesen.kotlinrestify.runtime.client.ktor.KtorHttpClientAdapter

/**
 * Default entrypoint — uses the Ktor-based implementation under the hood.
 * You can configure ApiConfig (baseUrl, retryPolicy, etc.) via the configure lambda.
 *
 * defaultRetryTimeoutMillis: total time budget used when the user did not supply a retryPolicy.
 */
fun api(configure: ApiConfig.() -> Unit): GeneratedApiCaller =
    api(adapter = null, defaultRetryTimeoutMillis = 10_000L, configure = configure)

/**
 * Entrypoint that allows a custom HttpClientAdapter and/or a custom GeneratedApiCaller factory to be provided.
 * If adapter == null the default KtorHttpClientAdapter is used.
 * If callerFactory is not provided the default will produce a KtorGeneratedApiCaller.
 *
 * defaultRetryTimeoutMillis: total time budget used when the user did not supply a retryPolicy.
 */
fun api(
    adapter: HttpClientAdapter? = null,
    defaultRetryTimeoutMillis: Long = 10_000L,
    callerFactory: (
        ApiConfig,
        HttpClientAdapter,
    ) -> GeneratedApiCaller = { cfg, ad -> KtorGeneratedApiCaller(cfg, ad) },
    configure: ApiConfig.() -> Unit,
): GeneratedApiCaller {
    val cfg = ApiConfig(baseUrl = "").apply(configure)

    // If caller didn't supply a retry policy, provide a sensible default that bounds the total time.
    if (cfg.retryPolicy == null) {
        cfg.retryPolicy = TimeBoundRetryPolicy(timeoutMillis = defaultRetryTimeoutMillis)
    }

    val actualAdapter = adapter ?: KtorHttpClientAdapter()

    // Use the provided factory to construct the GeneratedApiCaller (default = Ktor)
    return callerFactory(cfg, actualAdapter)
}
