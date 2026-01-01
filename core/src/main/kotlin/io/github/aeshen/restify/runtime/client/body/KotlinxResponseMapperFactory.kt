package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.serialization.kotlinx.JsonProviders
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Convenience extension to support the existing generator calling `mapperFactory.forKotlinx(...)`.
 *
 * This keeps generated code unchanged while keeping the factory implementations generic.
 */
fun <T> ResponseMapperFactory.forKotlinx(
    strategy: DeserializationStrategy<T>,
    json: Json = JsonProviders.defaultJson,
): ResponseMapper<T> =
    fromBytes { bytes ->
        if (bytes == null) {
            throw IllegalStateException("Response body is not present")
        }
        val bodyStr = String(bytes, Charsets.UTF_8)
        json.decodeFromString(strategy, bodyStr)
    }
