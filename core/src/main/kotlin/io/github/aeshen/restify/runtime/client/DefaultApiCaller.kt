package io.github.aeshen.restify.runtime.client

import io.github.aeshen.restify.runtime.ApiCaller
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.client.exception.ApiCallException
import io.github.aeshen.restify.runtime.retry.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T {
        // ensure coroutine cancellation is respected early
        currentCoroutineContext().ensureActive()

        val retryPolicy: RetryPolicy =
            transport.effectiveRetryPolicyFor(request)
                ?: throw IllegalStateException(
                    "RetryPolicy not resolved. Ensure ApiClientFactory provides a resolved retryPolicy on ApiConfig",
                )

        try {
            val response =
                retryPolicy.retry {
                    // check cancellation on each retry attempt
                    currentCoroutineContext().ensureActive()
                    transport.execute(request, mapper)
                }

            try {
                return mapper.map(response)
            } catch (mapEx: CancellationException) {
                // preserve coroutine cancellation
                throw mapEx
            } catch (mapEx: Exception) {
                throw ApiCallException("Failed to map response", request, response, mapEx)
            }
        } catch (ce: CancellationException) {
            // propagate cancellation without wrapping
            throw ce
        } catch (ex: Exception) {
            // wrap transport/retry errors with request context
            throw ApiCallException("Request execution failed", request, null, ex)
        }
    }
}
