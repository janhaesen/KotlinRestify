package io.github.aeshen.restify.annotation.http

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HttpPatch(
    val path: String,
)
