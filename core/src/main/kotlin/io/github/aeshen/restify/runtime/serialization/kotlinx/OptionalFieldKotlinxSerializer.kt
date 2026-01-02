package io.github.aeshen.restify.runtime.serialization.kotlinx

import io.github.aeshen.restify.runtime.serialization.OptionalField
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * JSON-first runtime serializer for OptionalField<Any?> used as a non-generic singleton.
 *
 * - Accepts both an "envelope" shape { "present": boolean, "value": ... } and a bare value (e.g. 1 or "x")
 *   when decoding JSON — bare values are treated as Present(value).
 * - Emits the envelope when encoding.
 * - This singleton is intentionally JSON-focused and avoids requiring a compiled serializer for kotlin.Any
 *   at init-time. For typed/non-JSON usage prefer the typed factory: OptionalFieldKotlinxSerializerImpl.of(...)
 *
 * Rationale:
 * - Keeps generated/legacy JSON payloads interoperable while providing a safe fallback for untyped scenarios.
 * - For production code where the element type is known, use the typed impl to preserve full typing.
 */
object OptionalFieldKotlinxSerializer : KSerializer<OptionalField<Any?>> {
    // use fully-qualified accessor to obtain a JsonElement serializer reliably
    private val jsonElemSerializer =
        JsonElement.serializer()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("OptionalField") {
            element("present", PrimitiveSerialDescriptor("present", PrimitiveKind.BOOLEAN))
            element("value", jsonElemSerializer.descriptor, isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: OptionalField<Any?>,
    ) {
        val composite = encoder.beginStructure(descriptor)
        try {
            when (value) {
                OptionalField.Absent -> {
                    composite.encodeBooleanElement(descriptor, 0, false)
                }

                is OptionalField.Present<*> -> {
                    composite.encodeBooleanElement(descriptor, 0, true)
                    if (encoder is JsonEncoder) {
                        val jsonElem = kotlinToJsonElement(value.value)
                        composite.encodeSerializableElement(
                            descriptor,
                            1,
                            jsonElemSerializer,
                            jsonElem,
                        )
                    } else {
                        throw SerializationException(
                            "OptionalFieldKotlinxSerializer (non-generic singleton) supports" +
                                " JSON only. Use OptionalFieldKotlinxSerializerImpl.of(...)" +
                                " or serializerFor<T>() for typed usage in other formats.",
                        )
                    }
                }
            }
        } finally {
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: Decoder): OptionalField<Any?> {
        // JSON-tolerant path: accept envelope object or bare value
        if (decoder is JsonDecoder) {
            val elem = decoder.decodeJsonElement()
            if (elem is JsonObject && (elem.containsKey("present") || elem.containsKey("value"))) {
                val present =
                    when (val presentElem = elem["present"]) {
                        is JsonPrimitive -> presentElem.booleanOrNull
                            ?: true
                        null -> true
                        else -> true
                    }
                if (!present) {
                    return OptionalField.absent()
                }

                val valueElem = elem["value"]
                    ?: JsonNull
                val parsedValue =
                    if (valueElem === JsonNull) {
                        null
                    } else {
                        jsonElementToKotlin(valueElem)
                    }
                return OptionalField.present(parsedValue)
            }

            // Bare value -> treat as Present(value)
            val value = if (elem === JsonNull) {
                null
            } else {
                jsonElementToKotlin(elem)
            }
            return OptionalField.present(value)
        }

        // Non-JSON composite path — decode envelope shape
        val composite = decoder.beginStructure(descriptor)
        var present = false
        var parsedValue: Any? = null

        try {
            loop@ while (true) {
                when (composite.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> {
                        break@loop
                    }

                    0 -> {
                        present = composite.decodeBooleanElement(descriptor, 0)
                    }

                    1 -> {
                        val raw =
                            composite.decodeSerializableElement(
                                descriptor,
                                1,
                                jsonElemSerializer,
                            )
                        parsedValue = jsonElementToKotlin(raw)
                    }

                    else -> {
                        break@loop
                    }
                }
            }
        } finally {
            composite.endStructure(descriptor)
        }

        return if (!present) {
            OptionalField.absent()
        } else {
            OptionalField.present(parsedValue)
        }
    }

    private fun kotlinToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> {
                JsonNull
            }

            is JsonElement -> {
                value
            }

            is String -> {
                JsonPrimitive(value)
            }

            is Number -> {
                JsonPrimitive(value)
            }

            // use Number constructor
            is Boolean -> {
                JsonPrimitive(value)
            }

            is Map<*, *> -> {
                val entries =
                    value.entries.associate { (k, v) ->
                        (k?.toString() ?: "null") to kotlinToJsonElement(v)
                    }
                JsonObject(entries)
            }

            is Iterable<*> -> {
                JsonArray(value.map { kotlinToJsonElement(it) })
            }

            is Array<*> -> {
                JsonArray(value.map { kotlinToJsonElement(it) })
            }

            else -> {
                JsonPrimitive(value.toString())
            }
        }

    private fun jsonElementToKotlin(elem: JsonElement): Any? =
        when (elem) {
            is JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                // try numeric/bool then fallback to string
                elem.intOrNull
                    ?: elem.longOrNull
                    ?: elem.doubleOrNull
                    ?: elem.booleanOrNull
                    ?: elem.content
            }

            is JsonArray -> {
                elem.map { jsonElementToKotlin(it) }
            }

            is JsonObject -> {
                elem.mapValues { jsonElementToKotlin(it.value) }
            }
        }
}
