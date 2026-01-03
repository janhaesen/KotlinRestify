package io.github.aeshen.restify.runtime.serialization.kotlinx

import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.client.body.serializer.SerializedBody
import io.github.aeshen.restify.runtime.client.body.serializer.impl.BaseBodySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.full.createType

internal class KotlinxBodySerializer(
    private val json: Json = Json,
) : BaseBodySerializer(),
    BodySerializer {
    override fun serialize(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody {
        // Delegate simple cases to base helper
        val simple = handleSimpleTypes(body, requestedContentType)
        if (simple != null) {
            return simple
        }

        // Complex types: try to obtain a Kotlinx serializer for the runtime type and encode to JSON string
        val requested = requestedContentType?.toString()
        val kType = body!!::class.createType(nullable = false)
        val ks: KSerializer<Any>? =
            try {
                @Suppress("UNCHECKED_CAST")
                serializer(kType) as KSerializer<Any>
            } catch (_: Exception) {
                null
            }

        if (ks != null) {
            val jsonStr = json.encodeToString(ks, body)
            return SerializedBody(jsonStr, requested ?: "application/json")
        } else {
            throw IllegalArgumentException(
                "KotlinxBodySerializer: cannot find serializer for type ${body::class}. " +
                    "Make the type @Serializable or provide a custom BodySerializer.",
            )
        }
    }

    override fun deserialize(
        rawPayload: Any?,
        contentType: MediaType?,
    ): ByteArray? {
        // Simple cases handled by base helper
        if (rawPayload == null) {
            return null
        }

        val simple = handleSimpleDeserialize(rawPayload)
        if (simple != null) {
            return simple
        }

        // Attempt to encode arbitrary object to JSON, otherwise fall back to toString()
        val bytesStr =
            try {
                val kType = rawPayload::class.createType(nullable = false)
                val ks: KSerializer<Any>? =
                    try {
                        @Suppress("UNCHECKED_CAST")
                        serializer(kType) as KSerializer<Any>
                    } catch (_: Exception) {
                        null
                    }
                if (ks != null) {
                    json.encodeToString(ks, rawPayload)
                } else {
                    rawPayload.toString()
                }
            } catch (_: Exception) {
                rawPayload.toString()
            }
        return bytesStr.toByteArray(Charsets.UTF_8)
    }
}
