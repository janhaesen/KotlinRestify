package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.ResponseData
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlin.reflect.full.memberProperties

class KotlinxResponseMapper<T>(
    private val json: Json,
    private val deserializer: DeserializationStrategy<T>,
) : ResponseMapper<T> {
    override suspend fun map(response: ResponseData): T {
        // Attempt to locate a payload property on ResponseData using common names.
        val payload =
            try {
                response::class
                    .memberProperties
                    .firstOrNull { it.name in setOf("body", "payload", "rawPayload", "content") }
                    ?.getter
                    ?.call(response)
            } catch (_: Throwable) {
                null
            }

        // Normalize to a String for JSON decoding
        val textPayload =
            when (payload) {
                is ByteArray -> String(payload, Charsets.UTF_8)
                is String -> payload
                null -> ""
                else -> payload.toString()
            }

        // Decode using the provided deserialization strategy
        return json.decodeFromString(deserializer, textPayload)
    }
}
