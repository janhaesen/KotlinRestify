package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.runtime.serialization.OptionalField
import tools.jackson.core.JsonParser
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode

/**
 * Jackson deserializer for OptionalField<T>.
 *
 * Expects the envelope produced by OptionalFieldJacksonSerializer:
 *  - { "present": false }  -> OptionalField.Absent
 *  - { "present": true, "value": <...> } -> OptionalField.Present(<deserialized value|null>)
 *
 * This deserializer is contextual: during contextualization it will attempt to obtain a deserializer
 * for the contained value type. If contextual information is unavailable it falls back to returning
 * the raw JsonNode inside Present to avoid throws in partial contexts.
 */
/**
 * Jackson deserializer for OptionalField<T>.
 *
 * Accepts both the envelope produced by OptionalFieldJacksonSerializer:
 *  - { "present": false }  -> OptionalField.Absent
 *  - { "present": true, "value": <...> } -> OptionalField.Present(<deserialized value|null>)
 *
 * And raw values (e.g. plain number/string/object) which are interpreted as Present(<value>).
 */
class OptionalFieldJacksonDeserializer(
    private val valueType: JavaType? = null,
    private val valueDeserializer: ValueDeserializer<Any?>? = null,
) : ValueDeserializer<OptionalField<*>>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): OptionalField<*> {
        val node = ctxt.readTree(p) ?: return OptionalField.Absent

        // If it's an ObjectNode and looks like the envelope, respect "present"
        if (node is ObjectNode) {
            val hasPresentField = node.has("present")
            if (hasPresentField) {
                val isPresent = node.path("present").asBoolean(false)
                if (!isPresent) return OptionalField.Absent
                val valueNode = node.get("value") ?: NullNode.instance
                return deserializeNode(valueNode, ctxt)
            }
            // If it's an object but not the envelope, treat the object itself as the inner value
            return deserializeNode(node, ctxt)
        }

        // Non-object JSON (primitive, array, null) -> treat as present value
        return deserializeNode(node, ctxt)
    }

    private fun deserializeNode(valueNode: JsonNode, ctxt: DeserializationContext): OptionalField<*> {
        if (valueDeserializer == null || valueType == null) {
            return OptionalField.Present(valueNode)
        }

        val innerParser = valueNode.traverse(ctxt)
        innerParser.nextToken()
        val value = valueDeserializer.deserialize(innerParser, ctxt)
        return OptionalField.Present(value)
    }

    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?,
    ): ValueDeserializer<*> {
        if (property == null) {
            return this
        }

        val wrapperType = property.type

        // Prefer OptionalField super-type if present (handles Jackson passing related types)
        val optionalSuper = wrapperType.findSuperType(OptionalField::class.java)
        val containedType: JavaType =
            optionalSuper?.containedType(0)
                ?: wrapperType.containedType(0)
                ?: ctxt.constructType(Any::class.java)

        val deser = ctxt.findContextualValueDeserializer(containedType, property)

        @Suppress("UNCHECKED_CAST")
        return OptionalFieldJacksonDeserializer(
            valueType = containedType,
            valueDeserializer = deser as ValueDeserializer<Any?>,
        )
    }
}
