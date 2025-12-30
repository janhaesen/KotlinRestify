package io.github.aeshen.restify.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class HttpPost(
    val path: String,
)
