package io.github.aeshen.restify.annotation.http

/**
 * Marks a function (or an interface) as a REST endpoint definition.
 *
 * @param path     Relative path, e.g. "/users/{id}"
 * @param description optional human‑readable description (used for OpenAPI generation)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RestEndpoint(
    val path: String,
    val description: String = "",
)
