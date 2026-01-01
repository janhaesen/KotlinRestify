package io.github.aeshen.example.api

import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.Resource
import io.github.aeshen.restify.annotation.param.Path
import io.github.aeshen.restify.annotation.param.Query
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.Optional

/**
 * Model types for User operations.
 *
 * - UserModel.Request: used for create/update operations (no id allowed).
 * - UserModel.Response: returned by read operations and includes a read-only id.
 */
@Serializable
sealed interface UserModel {
    @Serializable
    data class Request(
        @Contextual
        val name: Optional<String> = Optional.empty(),
    ) : UserModel

    @Serializable
    data class Response(
        @Contextual
        val id: Optional<String> = Optional.empty(),
        @Contextual
        val name: Optional<String> = Optional.empty(),
    ) : UserModel
}

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
    ): UserModel.Response
}
