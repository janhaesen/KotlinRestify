package io.github.aeshen.example.api

import io.github.aeshen.kotlinrestify.runtime.ResponseData
import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.HttpPost
import io.github.aeshen.restify.annotation.http.Resource
import io.github.aeshen.restify.annotation.param.Body
import io.github.aeshen.restify.annotation.param.Path
import io.github.aeshen.restify.annotation.param.Query

/**
 * Examples that should trigger processor validation errors:
 *  - multiple @Body parameters
 *  - @Query(required = true) with nullable type
 *  - mismatching @Path parameter name vs path placeholder
 */
@Resource(
    path = "/posts",
)
interface InvalidApi {
    // -> validation: more than one @Body
    @HttpPost(path = "")
    suspend fun createMultipleBodies(
        @Body body1: Any,
        @Body body2: Any,
    ): ResponseData

    // -> validation: required query must be non-nullable
    @HttpGet(path = "/search")
    suspend fun searchRequiredNullable(
        @Query(name = "q", required = true) q: String?,
    ): ResponseData

    // -> validation: placeholder {id} not matched by @Path("sku")
    @HttpGet(path = "/items/{id}")
    suspend fun getItemWithWrongPathAnnotation(
        @Path("sku") sku: String,
    ): ResponseData
}
