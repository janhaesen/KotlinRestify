package io.github.aeshen.kotlinrestify.runtime

import io.github.aeshen.restify.annotation.http.HttpMethod
import io.github.aeshen.restify.annotation.http.MediaType

data class RequestData(
    val method: HttpMethod,
    val urlPath: String,
    val headers: Map<String, String> = emptyMap(),
    val queryParameters: Map<String, String> = emptyMap(),
    val body: Any? = null, // typically a DTO or primitive; adapter is responsible for serialization
    val contentType: MediaType? = null,
)
