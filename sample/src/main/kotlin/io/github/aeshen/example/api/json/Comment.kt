package io.github.aeshen.example.api.json

import io.github.aeshen.restify.runtime.serialization.OptionalField
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val postId: OptionalField<Int>,
    val id: OptionalField<Int>,
    val name: OptionalField<String>,
    val email: OptionalField<String>,
    val body: OptionalField<String>,
)
