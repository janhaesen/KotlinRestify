package io.github.aeshen.restify.runtime.client.body

import kotlin.reflect.KClass

/**
 * Neutral key describing the logical type the generated code requests.
 * - ClassKey: a single concrete class (nullable flag indicates T?)
 * - ListKey: a List<T> where elementKlass and elementNullable describe the element type
 */
sealed class TypeKey {
    data class ClassKey(
        val clazz: KClass<out Any>,
        val nullable: Boolean = false,
    ) : TypeKey()

    data class ListKey(
        val elementClazz: KClass<out Any>,
        val elementNullable: Boolean = false,
    ) : TypeKey()
}
