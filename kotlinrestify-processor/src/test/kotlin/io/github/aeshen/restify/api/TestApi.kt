package io.github.aeshen.restify.api

import io.github.aeshen.restify.annotation.HttpGet
import io.github.aeshen.restify.annotation.HttpMethod
import io.github.aeshen.restify.annotation.RestEndpoint

@RestEndpoint(
    path = "/users",
    description = "User endpoint",
)
class TestApi {
    @HttpGet("/users")
    fun getUsers(): List<String> = emptyList()
}
