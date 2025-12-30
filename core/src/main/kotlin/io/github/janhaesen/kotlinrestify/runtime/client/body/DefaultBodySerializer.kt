package io.github.janhaesen.kotlinrestify.runtime.client.body

import io.github.aeshen.restify.annotation.http.MediaType

/**
 * Default serializer used by adapters when no custom serializer is provided.
 * Supports:
 *  - ByteArray -> application/octet-stream
 *  - String -> application/json (default) or requestedContentType if provided
 *  - primitive/wrapper/Number/Boolean/Char -> treated as text/plain
 *
 * For complex DTOs a user must provide a custom BodySerializer (e.g. using kotlinx.serialization or Jackson).
 */
object DefaultBodySerializer : BodySerializer {
    override fun serialize(
        body: Any,
        requestedContentType: MediaType?,
    ): SerializedBody {
        val contentType = requestedContentType?.toString()
        return when (body) {
            is ByteArray -> SerializedBody(body, contentType ?: "application/octet-stream")

            is String -> SerializedBody(body, contentType ?: "application/json")

            is Number, is Boolean, is Char -> SerializedBody(body.toString(), contentType ?: "text/plain")

            else -> throw IllegalArgumentException(
                "Unsupported body type `${body::class}` for DefaultBodySerializer. " +
                    "Provide a custom BodySerializer via ApiConfig.bodySerializer to serialize DTOs to JSON or another media type.",
            )
        }
    }
}
