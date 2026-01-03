package io.github.aeshen.restify.runtime.serialization

import io.github.aeshen.restify.runtime.client.body.BodySerializer
import io.github.aeshen.restify.runtime.serialization.jackson.JacksonBodySerializer
import io.github.aeshen.restify.runtime.serialization.kotlinx.KotlinxBodySerializer
import kotlinx.serialization.json.Json
import tools.jackson.databind.ObjectMapper

object BodySerializerFactory {
    /**
     * Public factory producing a `BodySerializer` backed by the internal `KotlinxBodySerializer`.
     *
     * Usage:
     *   ApiConfig.builder(baseUrl)
     *     .bodySerializer(createKotlinxBodySerializer(Json { ignoreUnknownKeys = true }))
     */
    fun createKotlinxBodySerializer(json: Json = Json.Default): BodySerializer =
        KotlinxBodySerializer(json)

    /**
     * Public factory producing a `BodySerializer` backed by the internal `JacksonBodySerializer`.
     *
     * Usage:
     *   ApiConfig.builder(baseUrl)
     *     .bodySerializer(createJacksonBodySerializer(ObjectMapper().apply { /* configure */ }))
     */
    fun createJacksonBodySerializer(objectMapper: ObjectMapper = ObjectMapper()): BodySerializer =
        JacksonBodySerializer(objectMapper)
}
