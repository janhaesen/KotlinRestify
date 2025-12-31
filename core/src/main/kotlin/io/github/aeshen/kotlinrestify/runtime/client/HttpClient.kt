package io.github.aeshen.kotlinrestify.runtime.client

import io.github.aeshen.kotlinrestify.runtime.ResponseData

interface HttpClient {
    suspend fun get(url: String): ResponseData

    suspend fun delete(url: String): ResponseData

    suspend fun post(
        url: String,
        body: Any?,
    ): ResponseData

    suspend fun put(
        url: String,
        body: Any?,
    ): ResponseData

    suspend fun patch(
        url: String,
        body: Any?,
    ): ResponseData
}
