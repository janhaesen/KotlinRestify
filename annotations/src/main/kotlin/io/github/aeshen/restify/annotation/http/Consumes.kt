package io.github.aeshen.restify.annotation.http

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Consumes(
    val value: MediaType = MediaType.APPLICATION_JSON,
)
