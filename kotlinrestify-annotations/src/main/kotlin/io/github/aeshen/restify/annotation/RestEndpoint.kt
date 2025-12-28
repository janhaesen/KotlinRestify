package io.github.aeshen.restify.annotation

/**
 * Marks a function (or an interface) as a REST endpoint definition.
 *
 * @param method   HTTP verb
 * @param path     Relative path, e.g. "/users/{id}"
 * @param description optional human‑readable description (used for OpenAPI generation)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RestEndpoint(
    val method: HttpMethod,
    val path: String,
    val description: String = ""
)
