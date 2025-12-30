package io.github.aeshen.restify.annotation.http

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Produces(
    val value: MediaType = MediaType.APPLICATION_JSON,
)
