package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.runtime.client.body.BodySerializer
import io.github.aeshen.restify.runtime.retry.RetryPolicy

data class ApiConfig(
    var baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val timeoutMillis: Long? = null,
    val bodySerializer: BodySerializer? = null,
    val retryPolicy: RetryPolicy? = null,
    val followRedirects: Boolean = true,
)
