package io.github.aeshen.restify.runtime.client.body.serializer.impl

import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.client.body.serializer.SerializedBody

/**
 * Base helper for concrete BodySerializer implementations.
 *
 * - handleSimpleTypes: serializes ByteArray, String, Number/Boolean/Char and `null` to an
 *  appropriate SerializedBody.
 * - handleSimpleDeserialize: turns ByteArray/String/null into ByteArray?; returns null for other
 *  types so callers may handle complex cases.
 */
internal abstract class BaseBodySerializer {
    protected fun handleSimpleTypes(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody? {
        if (body == null) {
            return SerializedBody(null, null)
        }

        val requested = requestedContentType?.toString()
        return when (body) {
            is ByteArray -> {
                SerializedBody(body, requested ?: "application/octet-stream")
            }

            is String -> {
                SerializedBody(body, requested ?: "application/json")
            }

            is Number,
            is Boolean,
            is Char,
            -> {
                SerializedBody(
                    body.toString(),
                    requested ?: "text/plain",
                )
            }

            else -> {
                null
            }
        }
    }

    protected fun handleSimpleDeserialize(rawPayload: Any?): ByteArray? {
        if (rawPayload == null) {
            return null
        }

        return when (rawPayload) {
            is ByteArray -> rawPayload
            is String -> rawPayload.toByteArray(Charsets.UTF_8)
            else -> null
        }
    }
}
