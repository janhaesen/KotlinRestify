package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.client.body.ResponseMapperFactory
import io.github.aeshen.restify.runtime.client.body.TypeKey
import java.io.ByteArrayInputStream
import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectMapper

/**
 * Produces `JacksonResponseMapper` instances for `TypeKey` values.
 *
 * - Handles `ClassKey` and `ListKey`.
 * - Uses Jackson `TypeFactory` to build the appropriate `JavaType`.
 */
class JacksonResponseMapperFactory(
    private val objectMapper: ObjectMapper,
) : ResponseMapperFactory {

    override fun <T> tryFor(key: TypeKey): ResponseMapper<T> {
        val javaType = when (key) {
            is TypeKey.ClassKey -> objectMapper.typeFactory.constructType(key.clazz.java)
            is TypeKey.ListKey -> objectMapper.typeFactory.constructCollectionType(
                List::class.java,
                key.elementClazz.java
            )
        }

        // Wrap the created JavaType into the JacksonResponseMapper
        return createMapper(
            javaType,
            when (key) {
                is TypeKey.ClassKey -> key.nullable
                is TypeKey.ListKey -> false // lists are represented as concrete List<T>; nullability is expressed with ClassKey where used
            }
        )
    }

    private fun <T> createMapper(javaType: JavaType, nullable: Boolean): ResponseMapper<T> =
        ResponseMapper { response: ResponseData ->
            val bytes = response.body ?: ByteArray(0)

            // Empty body handling:
            // - if nullable: return null immediately
            // - if non-nullable: attempt to deserialize "null" so Jackson/Kotlin module can enforce defaults / throw;
            //   if that yields null, throw to signal missing body for non-nullable target.
            if (bytes.isEmpty()) {
                if (nullable) {
                    @Suppress("UNCHECKED_CAST")
                    return@ResponseMapper null as T
                }

                return@ResponseMapper objectMapper.readValue("null".byteInputStream(), javaType)
                    ?: throw UnsupportedOperationException("Empty response body for non-nullable target: $javaType")
            }

            // Non-empty body: deserialize from InputStream to avoid overload inference issues.
            val result = objectMapper.readValue<T>(ByteArrayInputStream(bytes), javaType)
            if (result != null || nullable) {
                return@ResponseMapper result
            }

            throw UnsupportedOperationException(
                "Deserialized null for non-nullable target: $javaType"
            )
        }
}

