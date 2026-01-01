package io.github.aeshen.example.api.json

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.Optional

@Serializable
data class Post(
    @Contextual
    val userId: Optional<Int>,
    @Contextual
    val id: Optional<Int>,
    @Contextual
    val title: Optional<String>,
    @Contextual
    val body: Optional<String>,
)
