package io.github.aeshen.restify.runtime.serialization.kotlinx

import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.client.body.BodySerializer
import io.github.aeshen.restify.runtime.client.body.SerializedBody
import kotlin.reflect.full.createType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class KotlinxBodySerializer(
    private val json: Json = Json,
) : BodySerializer {

    override fun serialize(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody {
        if (body == null) {
            return SerializedBody(null, null)
        }

        val requested = requestedContentType?.toString()
        return when (body) {
            is ByteArray -> SerializedBody(body, requested ?: "application/octet-stream")

            is String -> SerializedBody(body, requested ?: "application/json")

            is Number,
            is Boolean,
            is Char -> SerializedBody(
                body.toString(),
                requested ?: "text/plain"
            )

            else -> {
                // Try to obtain a Kotlinx serializer for the runtime type and encode to JSON string
                val kType = body::class.createType(nullable = false)
                val ks: KSerializer<Any>? = try {
                    @Suppress("UNCHECKED_CAST")
                    serializer(kType) as KSerializer<Any>
                } catch (_: Exception) {
                    null
                }

                if (ks != null) {
                    val jsonStr = json.encodeToString(ks, body)
                    SerializedBody(jsonStr, requested ?: "application/json")
                } else {
                    throw IllegalArgumentException(
                        "KotlinxBodySerializer: cannot find serializer for type ${body::class}. " +
                            "Make the type @Serializable or provide a custom BodySerializer."
                    )
                }
            }
        }
    }

    override fun deserialize(
        rawPayload: Any?,
        contentType: MediaType?,
    ): ByteArray? {
        return when (rawPayload) {
            is ByteArray -> rawPayload

            is String -> rawPayload.toByteArray(Charsets.UTF_8)

            null -> null

            else -> {
                // Attempt to encode arbitrary object to JSON, otherwise fall back to toString()
                val bytesStr = try {
                    val kType = rawPayload::class.createType(nullable = false)
                    val ks: KSerializer<Any>? = try {
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
                bytesStr.toByteArray(Charsets.UTF_8)
            }
        }
    }
}
