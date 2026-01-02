package io.github.aeshen.restify.runtime.serialization.kotlinx

import io.github.aeshen.restify.runtime.serialization.OptionalField
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Simple kotlinx serializer that represents OptionalField as an object:
 *  { "present": true, "value": <value|null> }  // present or present-null
 *  { "present": false }                        // absent
 *
 * This keeps the three-state semantics explicit and avoids surprises when omitting fields.
 *
 * Note: this serializer is intentionally generic by using JsonElement for the inner value.
 * For strong, typed decoding you can supply a more specific decoding step at usage time.
 */
object OptionalFieldKotlinxSerializer : KSerializer<OptionalField<Any?>> {
    @OptIn(InternalSerializationApi::class)
    private val descriptorImpl =
        buildSerialDescriptor("io.github.aeshen.restify.runtime.OptionalField", StructureKind.CLASS) {
            element<Boolean>("present")
            element<JsonElement?>("value", isOptional = true)
        }

    override val descriptor: SerialDescriptor = descriptorImpl

    override fun serialize(encoder: Encoder, value: OptionalField<Any?>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("OptionalFieldKotlinxSerializer requires Json format")

        val obj = when (value) {
            OptionalField.Absent -> buildJsonObject { put("present", JsonPrimitive(false)) }
            is OptionalField.Present -> buildJsonObject {
                put("present", JsonPrimitive(true))
                val v = value.value
                put("value", if (v == null) JsonNull else jsonEncoder.json.encodeToJsonElement(v))
            }
        }

        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): OptionalField<Any?> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("OptionalFieldKotlinxSerializer requires Json format")

        val tree = jsonDecoder.decodeJsonElement()
        if (tree !is JsonObject) {
            throw SerializationException("Expected JsonObject for OptionalField")
        }

        val presentElem = tree["present"]
            ?: return OptionalField.Absent
        val present = (presentElem as? JsonPrimitive)?.booleanOrNull
            ?: false
        return if (!present) {
            OptionalField.Absent
        } else {
            val v = tree["value"] ?: JsonNull
            // return raw JsonElement wrapped; caller can decode further if needed.
            when (v) {
                JsonNull -> OptionalField.Present<Any?>(null)
                else -> OptionalField.Present<Any?>(v)
            }
        }
    }
}
