package io.github.aeshen.restify.annotation.http

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HttpPut(
    val path: String,
)
