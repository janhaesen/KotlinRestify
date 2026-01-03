package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer

/**
 * Unified factory API: request a mapper by a neutral TypeKey.
 * Implementations provide a nullable lookup via `tryFor`. Callers that require a
 * non-nullable mapper can use `forType<T>` which will throw when no mapper is available.
 */
interface ResponseMapperFactory {
    val bodySerializer: BodySerializer

    /** Return a mapper for the requested logical `TypeKey`, or null when this factory can't handle the key. */
    fun <T> tryFor(key: TypeKey): ResponseMapper<T>?

    /**
     * Convenience non-nullable accessor used by generated code.
     * Attempts to cast the nullable mapper to `ResponseMapper<T>` and throws if unavailable.
     */
    fun <T> forType(key: TypeKey): ResponseMapper<T> =
        tryFor(key)
            ?: throw UnsupportedOperationException("No ResponseMapper found for key: $key")
}
