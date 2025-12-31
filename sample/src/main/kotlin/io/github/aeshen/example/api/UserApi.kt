package io.github.aeshen.example.api

import io.github.aeshen.kotlinrestify.runtime.ResponseData
import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.Resource
import io.github.aeshen.restify.annotation.param.Path
import io.github.aeshen.restify.annotation.param.Query

/**
 * Valid example: single @Body / query optional / path param matches placeholder.
 */
@Resource(
    path = "/users",
)
interface UserApi {
    @HttpGet(path = "/{id}")
    suspend fun getUser(
        @Path("id") id: String,
        @Query(name = "expand", required = false) expand: String?,
    ): ResponseData
}
