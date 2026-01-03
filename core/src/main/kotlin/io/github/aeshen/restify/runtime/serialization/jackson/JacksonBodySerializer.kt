package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.client.body.serializer.SerializedBody
import io.github.aeshen.restify.runtime.client.body.serializer.impl.BaseBodySerializer
import tools.jackson.databind.ObjectMapper

internal class JacksonBodySerializer(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : BaseBodySerializer(),
    BodySerializer {

    @Suppress("TooGenericExceptionCaught")
    override fun serialize(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody {
        // Delegate simple cases to base helper
        val simple = handleSimpleTypes(body, requestedContentType)
        if (simple != null) {
            return simple
        }

        // Complex types: use Jackson to produce JSON string
        val requested = requestedContentType?.toString()
        try {
            val jsonStr = objectMapper.writeValueAsString(body)
            return SerializedBody(jsonStr, requested ?: "application/json")
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "JacksonBodySerializer: failed to serialize type ${body?.let {
                    it::class
                } ?: "null"}: ${e.message}",
                e,
            )
        }
    }

    override fun deserialize(
        rawPayload: Any?,
        contentType: MediaType?,
    ): ByteArray? {
        if (rawPayload == null) {
            return null
        }

        // Simple cases handled by base helper
        val simple = handleSimpleDeserialize(rawPayload)
        if (simple != null) {
            return simple
        }

        // Convert arbitrary object to JSON string when possible, otherwise fallback to toString()
        val bytesStr =
            try {
                objectMapper.writeValueAsString(rawPayload)
            } catch (_: Exception) {
                rawPayload.toString()
            }

        return bytesStr.toByteArray(Charsets.UTF_8)
    }
}
