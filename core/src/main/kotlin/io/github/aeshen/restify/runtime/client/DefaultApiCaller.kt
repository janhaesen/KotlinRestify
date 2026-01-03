package io.github.aeshen.restify.runtime.client

import io.github.aeshen.restify.runtime.ApiCaller
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.retry.RetryPolicy

/**
 * Simplified ApiCaller that delegates to a single transport execution method,
 * applies the configured retry policy and then maps the response.
 *
 * - `transport` is the AdapterHttpClient (wraps HttpClientAdapter + ApiConfig).
 * The caller asks the transport for the effective retry policy and expects it to be present.
 */
internal class DefaultApiCaller(
    private val transport: AdapterHttpClient,
) : ApiCaller {
    override suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T {
        // derive the effective retry policy via the transport (narrow surface)
        val retryPolicy: RetryPolicy =
            transport.effectiveRetryPolicyFor(request)
                ?: throw IllegalStateException(
                    "RetryPolicy not resolved. Ensure ApiClientFactory provides a resolved retryPolicy on ApiConfig",
                )

        val response =
            retryPolicy.retry {
                // AdapterHttpClient exposes execute(request) and uses its configured ApiConfig internally.
                transport.execute(request, mapper)
            }

        return mapper.map(response)
    }
}
