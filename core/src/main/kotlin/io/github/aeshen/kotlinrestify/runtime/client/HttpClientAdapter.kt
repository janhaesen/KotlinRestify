package io.github.aeshen.kotlinrestify.runtime.client

import io.github.aeshen.kotlinrestify.runtime.ApiConfig
import io.github.aeshen.kotlinrestify.runtime.RequestData
import io.github.aeshen.kotlinrestify.runtime.ResponseData

/**
 * Adapter interface that wraps the HTTP client implementation (e.g., Ktor).
*/
interface HttpClientAdapter {
    suspend fun execute(
        request: RequestData,
        config: ApiConfig,
    ): ResponseData
}
