package io.github.aeshen.restify.runtime.serialization.kotlinx

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.plus
import kotlinx.serialization.serializer
import java.util.Optional

/**
 * Generic KSerializer for java.util.Optional<T>.
 *
 * Encodes Optional.empty() as JSON null and decodes JSON null -> Optional.empty().
 */
@OptIn(ExperimentalSerializationApi::class)
internal class OptionalKSerializerImpl<T>(
    private val elementSerializer: KSerializer<T>,
) : KSerializer<Optional<T>> {
    override val descriptor: SerialDescriptor = elementSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Optional<T>,
    ) {
        if (value.isPresent) {
            encoder.encodeSerializableValue(elementSerializer, value.get())
        } else {
            encoder.encodeNull()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Optional<T> =
        if (decoder.decodeNotNullMark()) {
            val v = decoder.decodeSerializableValue(elementSerializer)
            Optional.ofNullable(v) as Optional<T>
        } else {
            decoder.decodeNull()
            Optional.empty<T>() as Optional<T>
        }
}

/**
 * Single module that provides a contextual factory for Optional\<T\>.
 *
 * The factory produces an Optional serializer when the element serializer is provided
 * by the serialization framework. This avoids registering concrete per-type contextual
 * serializers which can lead to duplicate-registration errors when modules are combined.
 */
fun optionalSerializersModule(): SerializersModule =
    SerializersModule {
        contextual(Optional::class) { typeArguments: List<KSerializer<*>> ->
            val elem =
                typeArguments.firstOrNull()
                    ?: throw IllegalArgumentException(
                        "Missing type argument for Optional<T> serializer",
                    )
            @Suppress("UNCHECKED_CAST")
            OptionalKSerializerImpl(
                elem as KSerializer<Any?>,
            ) as KSerializer<Any?>
        }
    }

/**
 * Json provider that uses the single optional factory module.
 * Keeps Json config centralized to avoid accidental re-registration.
 */
object JsonProviders {
    val defaultModule: SerializersModule =
        optionalSerializersModule()

    val defaultJson: Json by lazy {
        Json {
            serializersModule = defaultModule
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
