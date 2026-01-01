package io.github.aeshen.restify.runtime.client.adapter

import io.github.aeshen.restify.runtime.ApiConfig
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData

/**
 * Adapter interface that wraps the HTTP client implementation (e.g., Ktor).
 *
 * Low-level adapter that maps runtime RequestData/ResponseData to the actual HTTP implementation.
*/
interface HttpClientAdapter {
    suspend fun execute(
        request: RequestData,
        config: ApiConfig,
    ): ResponseData

    /**
     * Close underlying resources (connection pools, threads, engines).
     * Non-suspending so it can be called during shutdown.
     */
    fun close()
}
