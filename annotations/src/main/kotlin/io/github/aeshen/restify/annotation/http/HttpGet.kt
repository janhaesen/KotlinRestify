package io.github.aeshen.restify.annotation.http

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HttpGet(
    val path: String,
)
