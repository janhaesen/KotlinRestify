package io.github.aeshen.restify.runtime.client.body

/**
 * Delegating factory: tries registered providers in order and throws when none can handle the key.
 */
class DelegatingResponseMapperFactory(
    private val factories: List<ResponseMapperFactory>,
) : ResponseMapperFactory {

    @Suppress("UNCHECKED_CAST")
    override fun <T> tryFor(key: TypeKey): ResponseMapper<T>? =
        factories.firstNotNullOfOrNull { it.tryFor(key) }
}
