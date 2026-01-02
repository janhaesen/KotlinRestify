package io.github.aeshen.example.api.json

import io.github.aeshen.restify.runtime.serialization.OptionalField
import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val userId: OptionalField<Int>,
    val id: OptionalField<Int>,
    val title: OptionalField<String>,
    val body: OptionalField<String>,
)
