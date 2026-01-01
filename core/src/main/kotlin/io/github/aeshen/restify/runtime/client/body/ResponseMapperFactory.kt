package io.github.aeshen.restify.runtime.client.body

/**
 * Generic factory abstraction to create ResponseMapper\<T\> instances from a bytes->T function.
 * This keeps the factory implementation serializer-agnostic and avoids switching logic.
 */
interface ResponseMapperFactory {
    fun <T> fromBytes(deserialize: (ByteArray?) -> T): ResponseMapper<T>
}

/**
 * Default, trivial implementation that yields a ResponseMapper delegating to the provided function.
 */
class DefaultResponseMapperFactory : ResponseMapperFactory {
    override fun <T> fromBytes(deserialize: (ByteArray?) -> T): ResponseMapper<T> =
        ResponseMapper { response ->
            deserialize(
                response.body,
            )
        }
}
