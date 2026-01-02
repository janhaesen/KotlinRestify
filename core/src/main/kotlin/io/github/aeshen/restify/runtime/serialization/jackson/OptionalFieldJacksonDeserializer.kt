package io.github.aeshen.restify.runtime.serialization.jackson

import io.github.aeshen.restify.runtime.serialization.OptionalField
import tools.jackson.core.JsonParser
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
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
class OptionalFieldJacksonDeserializer(
    private val valueType: JavaType? = null,
    private val valueDeserializer: ValueDeserializer<Any?>? = null
) : ValueDeserializer<OptionalField<*>>() {

    /**
     * Deserialize the OptionalField envelope.
     *
     * If a contextual deserializer was resolved we use it to decode the inner "value" node; otherwise
     * the raw JsonNode is returned inside Present as a safe fallback.
     */
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): OptionalField<*> {
        val root = ctxt.readTree(p) as? ObjectNode
            ?: return OptionalField.Absent

        val isPresent = root.path("present").asBoolean(false)
        if (!isPresent) {
            return OptionalField.Absent
        }

        val valueNode = root.get("value")
            ?: NullNode.instance

        // If no contextual deserializer or JavaType was resolved, return the raw node wrapped in Present.
        if (valueDeserializer == null || valueType == null) {
            return OptionalField.Present(valueNode)
        }

        // Create a parser for the nested node and position it for the deserializer.
        val innerParser = valueNode.traverse(ctxt)
        innerParser.nextToken()

        val value = valueDeserializer.deserialize(innerParser, ctxt)
        return OptionalField.Present(value)
    }

    /**
     * Contextualization hook for resolving a deserializer for the contained type.
     */
    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?
    ): ValueDeserializer<*> {
        val wrapperType = property?.type
            ?: return this
        val containedType = wrapperType.containedType(0) ?: ctxt.constructType(Any::class.java)

        val deser = ctxt.findContextualValueDeserializer(containedType, property)

        @Suppress("UNCHECKED_CAST")
        return OptionalFieldJacksonDeserializer(
            valueType = containedType,
            valueDeserializer = deser as ValueDeserializer<Any?>
        )
    }
}
