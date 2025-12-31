package io.github.aeshen.kotlinrestify.runtime

/** High-level contract used by generated stubs to perform requests. */
interface GeneratedApiCaller {
    suspend fun <T> call(
        request: RequestData,
        mapper: ResponseMapper<T>,
    ): T
}
