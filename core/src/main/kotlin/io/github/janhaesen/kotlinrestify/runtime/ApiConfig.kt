package io.github.janhaesen.kotlinrestify.runtime

import io.github.janhaesen.kotlinrestify.runtime.client.body.BodySerializer

data class ApiConfig(
    var baseUrl: String,
    var defaultHeaders: Map<String, String> = emptyMap(),
    var timeoutMillis: Long = 30_000,
    var retryPolicy: RetryPolicy? = null,
    val bodySerializer: BodySerializer? = null,
)
