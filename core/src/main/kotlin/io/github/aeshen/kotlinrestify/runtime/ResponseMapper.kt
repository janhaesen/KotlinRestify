package io.github.aeshen.kotlinrestify.runtime

/** Maps raw ResponseData into a typed result returned by generated code. */
fun interface ResponseMapper<T> {
    suspend fun map(response: ResponseData): T
}
