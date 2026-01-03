package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.body.PayloadUtils
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.client.body.ResponseMapperFactory
import io.github.aeshen.restify.runtime.client.body.TypeKey
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream

/**
 * Produces `JacksonResponseMapper` instances for `TypeKey` values.
 *
 * - Handles `ClassKey` and `ListKey`.
 * - Uses Jackson `TypeFactory` to build the appropriate `JavaType`.
 */
class JacksonResponseMapperFactory(
    private val objectMapper: ObjectMapper,
    override val bodySerializer: BodySerializer = JacksonBodySerializer(objectMapper),
) : ResponseMapperFactory {
    override fun <T> tryFor(key: TypeKey): ResponseMapper<T> {
        val javaType =
            when (key) {
                is TypeKey.ClassKey -> {
                    objectMapper.typeFactory.constructType(key.clazz.java)
                }

                is TypeKey.ListKey -> {
                    objectMapper.typeFactory.constructCollectionType(
                        List::class.java,
                        key.elementClazz.java,
                    )
                }
            }

        // Wrap the created JavaType into the JacksonResponseMapper
        return createMapper(
            javaType,
            when (key) {
                is TypeKey.ClassKey -> key.nullable

                // lists are represented as concrete List<T>; nullability is expressed with
                // ClassKey where used
                is TypeKey.ListKey -> false
            },
        )
    }

    private fun <T> createMapper(
        javaType: JavaType,
        nullable: Boolean,
    ): ResponseMapper<T> =
        object : ResponseMapper<T> {
            override val bodySerializer: BodySerializer =
                this@JacksonResponseMapperFactory.bodySerializer

            override suspend fun map(response: ResponseData): T {
                val bytes = PayloadUtils.bytesOrEmpty(response.body)

                // Empty body handling:
                if (bytes.isEmpty()) {
                    if (nullable) {
                        @Suppress("UNCHECKED_CAST")
                        return null as T
                    }

                    return objectMapper.readValue("null".byteInputStream(), javaType)
                        ?: throw UnsupportedOperationException(
                            "Empty response body for non-nullable target: $javaType",
                        )
                }

                val result = objectMapper.readValue<T>(ByteArrayInputStream(bytes), javaType)
                if (result != null || nullable) {
                    return result
                }

                throw UnsupportedOperationException(
                    "Deserialized null for non-nullable target: $javaType",
                )
            }
        }
}
