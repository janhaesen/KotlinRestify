package io.github.aeshen.restify.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HttpGet(
    val path: String,
)
