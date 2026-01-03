package io.github.aeshen.restify.runtime.client.body.serializer.impl

import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.client.body.serializer.SerializedBody

/**
 * Default serializer used by adapters when no custom serializer is provided.
 * Supports:
 *  - ByteArray -> application/octet-stream
 *  - String -> application/json (default) or requestedContentType if provided
 *  - primitive/wrapper/Number/Boolean/Char -> treated as text/plain
 *
 * For complex DTOs a user must provide a custom BodySerializer (e.g. using kotlinx.serialization or Jackson).
 */
internal object DefaultBodySerializer : BaseBodySerializer(), BodySerializer {
    override fun serialize(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody {
        val simple = handleSimpleTypes(body, requestedContentType)
        if (simple != null) {
            return simple
        }

        throw IllegalArgumentException(
            "Unsupported body type `${body!!::class}` for DefaultBodySerializer. " +
                "Provide a custom BodySerializer via ApiConfig.bodySerializer to " +
                "serialize DTOs to JSON or another media type.",
        )
    }

    override fun deserialize(
        rawPayload: Any?,
        contentType: MediaType?,
    ): ByteArray? {
        if (rawPayload == null) {
            return null
        }

        val simple = handleSimpleDeserialize(rawPayload)
        if (simple != null) {
            return simple
        }

        throw IllegalArgumentException(
            "Unsupported raw payload type `${rawPayload::class}` for DefaultBodySerializer.",
        )
    }
}
