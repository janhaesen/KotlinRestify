package io.github.janhaesen.kotlinrestify.runtime.client

import io.github.janhaesen.kotlinrestify.runtime.ApiConfig
import io.github.janhaesen.kotlinrestify.runtime.GeneratedApiCaller
import io.github.janhaesen.kotlinrestify.runtime.RequestData
import io.github.janhaesen.kotlinrestify.runtime.ResponseData
import io.github.janhaesen.kotlinrestify.runtime.ResponseMapper

/** Simple factory/DSL to create a GeneratedApiCaller */
fun api(configure: ApiConfig.() -> Unit): GeneratedApiCaller {
    val cfg = ApiConfig(baseUrl = "").apply(configure)

    // Default wiring: you will provide an implementation that wires an HttpClientAdapter,
    // optional RetryPolicy and returns a GeneratedApiCaller. Here is a minimal placeholder.
    return object : GeneratedApiCaller {
        private val adapter: HttpClientAdapter =
            object : HttpClientAdapter {
                override suspend fun execute(
                    request: RequestData,
                    config: ApiConfig,
                ): ResponseData {
                    error("No HttpClientAdapter configured; replace with real implementation")
                }
            }

        override suspend fun <T> call(
            request: RequestData,
            mapper: ResponseMapper<T>,
        ): T {
            val exec = suspend { adapter.execute(request, cfg) }
            val response = cfg.retryPolicy?.retry { exec() } ?: exec()
            return mapper.map(response)
        }
    }
}
