package io.github.aeshen.restify.runtime.client

import io.github.aeshen.restify.runtime.ApiCaller
import io.github.aeshen.restify.runtime.DEFAULT_TIMEOUT_MILLIS
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.retry.RetryPolicy
import io.github.aeshen.restify.runtime.retry.TimeBoundRetryPolicy

/**
 * Simplified ApiCaller that delegates to a single transport execution method,
 * applies the configured retry policy and then maps the response.
 *
 * - `transport` is the AdapterHttpClient (wraps HttpClientAdapter + ApiConfig).
 * The caller no longer reads `transport.baseConfig` directly; it asks the transport for the effective retry policy.
 */
internal class DefaultApiCaller(
    private val transport: AdapterHttpClient,
    private val fallbackRetryPolicy: RetryPolicy =
        TimeBoundRetryPolicy(
            DEFAULT_TIMEOUT_MILLIS,
        ),
) : ApiCaller {
    override suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T {
        // derive the effective retry policy via the transport (narrow surface)
        val retryPolicy: RetryPolicy =
            transport.effectiveRetryPolicyFor(request) ?: fallbackRetryPolicy

        val response =
            retryPolicy.retry {
                // AdapterHttpClient exposes execute(request) and uses its configured ApiConfig internally.
                transport.execute(request)
            }

        return mapper.map(response)
    }
}
