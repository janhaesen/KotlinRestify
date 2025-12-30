package io.github.janhaesen.kotlinrestify.runtime.client

import io.github.janhaesen.kotlinrestify.runtime.ApiConfig
import io.github.janhaesen.kotlinrestify.runtime.RequestData
import io.github.janhaesen.kotlinrestify.runtime.ResponseData

/** Adapter interface that wraps the HTTP client implementation (e.g., Ktor). */
interface HttpClientAdapter {
    suspend fun execute(
        request: RequestData,
        config: ApiConfig,
    ): ResponseData
}
