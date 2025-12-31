package io.github.aeshen.kotlinrestify.runtime.client.ktor

import io.github.aeshen.kotlinrestify.runtime.ApiConfig
import io.github.aeshen.kotlinrestify.runtime.GeneratedApiCaller
import io.github.aeshen.kotlinrestify.runtime.RequestData
import io.github.aeshen.kotlinrestify.runtime.ResponseMapper
import io.github.aeshen.kotlinrestify.runtime.client.HttpClientAdapter

class KtorGeneratedApiCaller(
    private val config: ApiConfig,
    private val adapter: HttpClientAdapter = KtorHttpClientAdapter(),
) : GeneratedApiCaller {
    override suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T {
        val response =
            config.retryPolicy?.retry { adapter.execute(request, config) }
                ?: adapter.execute(request, config)
        return mapper.map(response)
    }
}
