package io.github.janhaesen.kotlinrestify.runtime.client.ktor

import io.github.janhaesen.kotlinrestify.runtime.ApiConfig
import io.github.janhaesen.kotlinrestify.runtime.GeneratedApiCaller
import io.github.janhaesen.kotlinrestify.runtime.RequestData
import io.github.janhaesen.kotlinrestify.runtime.ResponseMapper
import io.github.janhaesen.kotlinrestify.runtime.client.HttpClientAdapter

class KtorGeneratedApiCaller(
    private val config: ApiConfig,
    private val adapter: HttpClientAdapter = KtorHttpClientAdapter(),
) : GeneratedApiCaller {
    override suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T {
        val response =
            if (config.retryPolicy != null) {
                config.retryPolicy!!.retry { adapter.execute(request, config) }
            } else {
                adapter.execute(request, config)
            }
        return mapper.map(response)
    }
}
