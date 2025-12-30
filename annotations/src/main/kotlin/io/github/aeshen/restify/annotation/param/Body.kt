package io.github.aeshen.restify.annotation.param

/**
 * Marks a parameter as the HTTP request body.
 * Only one parameter per function may be annotated with @Body.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Body
