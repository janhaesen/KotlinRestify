package io.github.aeshen.example.api.json

import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.HttpPost
import io.github.aeshen.restify.annotation.http.Resource
import io.github.aeshen.restify.annotation.param.Body
import io.github.aeshen.restify.annotation.param.Path

@Resource(
    path = "/posts",
)
interface JsonPlaceholderApi {
    @HttpGet
    suspend fun getPosts(): List<Post>

    @HttpGet(path = "/{id}")
    suspend fun getPost(
        @Path("id") id: Int,
    ): Post?

    @HttpGet(path = "/{id}/comments")
    suspend fun getComments(
        @Path("id") postId: Int,
    ): List<Comment>

    @HttpPost
    suspend fun createPost(
        @Body post: Post,
    ): Post
}
