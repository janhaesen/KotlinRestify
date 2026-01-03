package io.github.aeshen.restify.runtime.client.body

import io.github.aeshen.restify.runtime.client.body.serializer.BodySerializer
import io.github.aeshen.restify.runtime.client.body.serializer.impl.DefaultBodySerializer

/**
 * Delegating factory: tries registered providers in order and throws when none can handle the key.
 */
internal class DelegatingResponseMapperFactory(
    private val factories: List<ResponseMapperFactory>,
) : ResponseMapperFactory {
    override val bodySerializer: BodySerializer =
        factories.firstNotNullOfOrNull { it.bodySerializer }
            ?: DefaultBodySerializer

    @Suppress("UNCHECKED_CAST")
    override fun <T> tryFor(key: TypeKey): ResponseMapper<T>? =
        factories.firstNotNullOfOrNull { it.tryFor(key) }
}
