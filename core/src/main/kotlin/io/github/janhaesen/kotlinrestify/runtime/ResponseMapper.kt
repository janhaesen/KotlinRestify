package io.github.janhaesen.kotlinrestify.runtime

/** Maps raw ResponseData into a typed result returned by generated code. */
fun interface ResponseMapper<T> {
    suspend fun map(response: ResponseData): T
}
