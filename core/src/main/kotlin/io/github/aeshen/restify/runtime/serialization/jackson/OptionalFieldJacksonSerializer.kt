package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.runtime.serialization.OptionalField
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.JavaType
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

/**
 * Jackson serializer for OptionalField<T>.
 *
 * Produces a small envelope object with a "present" boolean and an optional "value" field:
 *  - Absent  -> { "present": false }
 *  - Present -> { "present": true, "value": <serialized value|null> }
 *
 * This serializer is contextual: when used as a bean property the serializer will attempt to
 * resolve a serializer for the inner value type so the inner value is serialized correctly.
 *
 * Note: the implementation intentionally keeps a defensive fallback to Any when the content type
 * can't be determined during contextualization.
 *
 * Example usage:
 *  - register via a Jackson module or use as a contextual serializer on a property.
 */
class OptionalFieldJacksonSerializer(
    private val valueType: JavaType? = null,
    private val valueSerializer: ValueSerializer<Any?>? = null
) : ValueSerializer<OptionalField<*>>() {

    /**
     * Serialize an OptionalField<T> to the envelope JSON shape.
     *
     * The method resolves a content serializer (if not already provided) and uses it to emit the
     * "value" entry when the OptionalField is Present. Absent simply writes "present": false.
     */
    override fun serialize(
        value: OptionalField<*>,
        gen: JsonGenerator,
        ctxt: SerializationContext
    ) {
        gen.writeStartObject()

        when (value) {
            OptionalField.Absent -> {
                // explicit absent marker
                gen.writeBooleanProperty("present", false)
            }

            is OptionalField.Present<*> -> {
                gen.writeBooleanProperty("present", true)
                gen.writeRawValue("value")

                // Resolve serializer once into a local variable to keep the call site readable.
                val effectiveType = valueType
                    ?: ctxt.constructType(Any::class.java)
                val resolvedSerializer = valueSerializer
                    ?: ctxt.findValueSerializer(effectiveType)

                @Suppress("UNCHECKED_CAST")
                (resolvedSerializer as ValueSerializer<Any?>)
                    .serialize(value.value, gen, ctxt)
            }
        }

        gen.writeEndObject()
    }

    /**
     * Contextualization hook. When used as a bean property Jackson will call this method
     * so we can resolve a serializer for the contained type T.
     */
    override fun createContextual(
        ctxt: SerializationContext,
        property: BeanProperty,
    ): ValueSerializer<*> {
        val wrapperType = property.type
        val containedType = wrapperType.containedType(0)
            ?: ctxt.constructType(Any::class.java)

        val ser = ctxt.findContentValueSerializer(containedType, property)

        @Suppress("UNCHECKED_CAST")
        return OptionalFieldJacksonSerializer(
            valueType = containedType,
            valueSerializer = ser as ValueSerializer<Any?>
        )
    }
}
