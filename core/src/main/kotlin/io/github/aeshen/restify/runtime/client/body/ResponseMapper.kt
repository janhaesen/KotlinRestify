package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer

/** Maps raw ResponseData into a typed result returned by generated code. */
interface ResponseMapper<T> {
    val bodySerializer: BodySerializer

    suspend fun map(response: ResponseData): T
}
