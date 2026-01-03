package io.github.aeshen.restify.runtime.client.exception

import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData

class ApiCallException(
    message: String,
    val request: RequestData,
    val response: ResponseData? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
