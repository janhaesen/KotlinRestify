package io.github.aeshen.restify.annotation

/**
 * Declares a function parameter as a query‑parameter.
 *
 * @param name   name used in the URL query string
 * @param required true → the generated client will enforce non‑null at compile time
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class QueryParam(
    val name: String,
    val required: Boolean = true,
)
