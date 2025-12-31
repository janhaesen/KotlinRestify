package io.github.aeshen.restify.api

import io.github.aeshen.restify.annotation.http.HttpGet
import io.github.aeshen.restify.annotation.http.Resource

@Resource(
    path = "/users",
    description = "User endpoint",
)
class TestApi {
    @HttpGet("/users")
    fun getUsers(): List<String> = emptyList()
}
