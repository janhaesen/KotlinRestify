package io.github.aeshen.restify.runtime.serialization

import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonDeserializer
import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonSerializer
import io.github.aeshen.restify.runtime.serialization.kotlinx.OptionalFieldKotlinxSerializer
import kotlinx.serialization.Serializable
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

/**
 * Wrapper for a property that can be in three states:
 *  - Absent: the property was not provided
 *  - Present(null): property explicitly present with `null`
 *  - Present(value): property explicitly present with a value
 *
 * Annotated so Jackson and kotlinx.serialization will pick up the provided serializers
 * without further configuration.
 */
@Serializable(with = OptionalFieldKotlinxSerializer::class)
@JsonSerialize(using = OptionalFieldJacksonSerializer::class)
@JsonDeserialize(using = OptionalFieldJacksonDeserializer::class)
sealed class OptionalField<out T> {
    object Absent : OptionalField<Nothing>()
    data class Present<T>(val value: T?) : OptionalField<T>()

    companion object {
        fun <T> absent(): OptionalField<T> = Absent
        fun <T> present(value: T?): OptionalField<T> = Present(value)
        fun <T> ofNullable(value: T?): OptionalField<T> = Present(value)
    }

    val isPresent: Boolean
        get() = this is Present

    fun <R> map(transform: (T?) -> R?): OptionalField<R> =
        when (this) {
            Absent -> Absent
            is Present -> Present(transform(this.value))
        }
}
