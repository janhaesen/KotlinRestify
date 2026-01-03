package io.github.aeshen.restify.processor.generator

import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName

const val BASE_PACKAGE = "io.github.aeshen.restify"
const val RUNTIME_PACKAGE = "$BASE_PACKAGE.runtime"

val collectionRawTypes =
    setOf(
        ClassName("kotlin.collections", "List"),
        ClassName("kotlin.collections", "Collection"),
        ClassName("kotlin.collections", "Set"),
        ClassName("kotlin.collections", "Iterable"),
        ClassName("kotlin", "Array"),
        ClassName("java.util", "List"),
        ClassName("java.util", "Collection"),
        ClassName("java.util", "Set"),
        ClassName("java.lang", "Iterable"),
    )

/**
 * Regex to find path placeholders like `{id}`.
 */
val placeholderRegex = "\\{([^}/]+)\\}".toRegex()

/**
 * Find a matching KSValueParameter for a placeholder name.
 * Matching strategy:
 *  - exact match
 *  - suffix match (e.g. placeholder "id" matches "postId")
 *  - prefix match (e.g. placeholder "user" matches "userId")
 */
fun findMatchingParam(
    placeholder: String,
    params: List<KSValueParameter>,
): KSValueParameter? {
    params
        .firstOrNull { it.name?.asString() == placeholder }
        ?.let { return it }

    params
        .firstOrNull {
            val n = it.name?.asString()
                ?: return@firstOrNull false
            n.endsWith(placeholder, ignoreCase = true)
        }
        ?.let { return it }

    params
        .firstOrNull {
            val n = it.name?.asString()
                ?: return@firstOrNull false
            n.startsWith(placeholder, ignoreCase = true)
        }
        ?.let { return it }

    return null
}

/**
 * Convenience extension to check if a KSValueParameter (nullable receiver allowed)
 * represents a nullable type.
 */
fun KSValueParameter?.isNullableParam(): Boolean =
    this?.type?.resolve()?.nullability == Nullability.NULLABLE

/**
 * Convenience to get argument name or fallback to placeholder.
 */
fun KSValueParameter?.argNameOr(fallback: String): String =
    this?.name?.asString()
        ?: fallback
