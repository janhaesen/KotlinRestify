package io.github.aeshen.restify.runtime.serialization.kotlinx

import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.body.PayloadUtils
import io.github.aeshen.restify.runtime.client.body.ResponseMapper
import io.github.aeshen.restify.runtime.client.body.ResponseMapperFactory
import io.github.aeshen.restify.runtime.client.body.TypeKey
import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * ResponseMapperFactory backed by a configured kotlinx.serialization Json instance.
 * Construct with your `Json { ... }` configuration and register via the builder.
 */
class KotlinxResponseMapperFactory(
    private val json: Json = Json,
    override val bodySerializer: BodySerializer = KotlinxBodySerializer(json),
) : ResponseMapperFactory {
    companion object {
        private val DEFAULT_CHARSET = Charsets.UTF_8
    }

    override fun <T> tryFor(key: TypeKey): ResponseMapper<T>? {
        return when (key) {
            is TypeKey.ClassKey -> {
                val kType = makeKType(key.clazz, key.nullable)
                val ks =
                    serializerFor(kType)
                        ?: return null
                responseMapperForSerializer(ks)
            }

            is TypeKey.ListKey -> {
                val elemType = makeKType(key.elementClazz, key.elementNullable)
                val elemSerializer =
                    serializerFor(elemType)
                        ?: return null

                @Suppress("UNCHECKED_CAST")
                val listSerializer = ListSerializer(elemSerializer as KSerializer<Any?>)
                responseMapperForSerializer(listSerializer)
            }
        }
    }

    private fun makeKType(
        kClass: KClass<out Any>,
        nullable: Boolean,
    ): KType =
        if (nullable) {
            kClass.createType(nullable = true)
        } else {
            kClass.createType()
        }

    private fun serializerFor(kType: KType): KSerializer<*>? =
        try {
            @Suppress("UNCHECKED_CAST")
            serializer(kType) as KSerializer<*>
        } catch (_: Exception) {
            null
        }

    private fun <T> responseMapperForSerializer(serializer: KSerializer<*>): ResponseMapper<T> =
        object : ResponseMapper<T> {
            override val bodySerializer: BodySerializer =
                this@KotlinxResponseMapperFactory.bodySerializer

            override suspend fun map(response: ResponseData): T {
                val str = responseBodyAsString(response)

                @Suppress("UNCHECKED_CAST")
                val s = serializer as KSerializer<T>
                return json.decodeFromString(s, str)
            }
        }

    private fun responseBodyAsString(response: ResponseData): String =
        PayloadUtils.bytesToString(response.body, DEFAULT_CHARSET)
}
