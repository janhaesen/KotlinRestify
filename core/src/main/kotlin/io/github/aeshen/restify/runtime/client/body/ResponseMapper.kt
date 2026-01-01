package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.ResponseData

/** Maps raw ResponseData into a typed result returned by generated code. */
fun interface ResponseMapper<T> {
    suspend fun map(response: ResponseData): T
}
