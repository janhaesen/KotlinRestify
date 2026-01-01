package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.runtime.client.body.ResponseMapper

/** High-level contract used by generated stubs to perform requests. */
interface ApiCaller {
    suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T
}
