package io.github.aeshen.example.api.json

import io.github.aeshen.example.api.json.JsonProviders.createJacksonJsonProvider
import io.github.aeshen.example.api.json.JsonProviders.kotlinJsonProvider
import io.github.aeshen.restify.generated.JsonPlaceholderApiClient
import io.github.aeshen.restify.runtime.ApiClientFactory
import io.github.aeshen.restify.runtime.client.body.BodySerializerFactory.createKotlinxBodySerializer
import io.github.aeshen.restify.runtime.serialization.OptionalField
import io.github.aeshen.restify.runtime.serialization.jackson.JacksonResponseMapperFactory
import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonDeserializer
import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonSerializer
import io.github.aeshen.restify.runtime.serialization.kotlinx.KotlinxResponseMapperFactory
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Json provider that uses the single optional factory module.
 * Keeps Json config centralized to avoid accidental re-registration.
 */
object JsonProviders {
    val kotlinJsonProvider: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    fun createJacksonJsonProvider(): ObjectMapper = JsonMapper.builder()
        .addModule(
            KotlinModule.Builder()
                .enable(KotlinFeature.StrictNullChecks)
                .build()
        ) // register Kotlin support
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // be tolerant in tests
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT) // optional convenience
        .addModule(
            SimpleModule("OptionalFieldModule")
                .addSerializer(OptionalField::class.java, OptionalFieldJacksonSerializer())
                .addDeserializer(OptionalField::class.java, OptionalFieldJacksonDeserializer())
        )
        .build()
}

class JsonPlaceholderApiClientIntegrationTest {
    companion object {
        @JvmStatic
        fun providerModes(): Stream<Arguments> = Stream.of(
            Arguments.of("jackson", true),
            Arguments.of("jackson", false),
            Arguments.of("kotlinx", true),
            Arguments.of("kotlinx", false),
        )
    }

    class JsonPlaceholderApiClientIntegrationTest {
        private fun providerModes(): List<Pair<String, Boolean>> = listOf(
            "jackson" to true,
            "jackson" to false,
            "kotlinx" to true,
            "kotlinx" to false,
        )

        private fun buildClient(
            providerName: String,
            useExplicitFactory: Boolean
        ): JsonPlaceholderApiClient {
            val mapperFactory = when (providerName) {
                "jackson" -> JacksonResponseMapperFactory(createJacksonJsonProvider())
                "kotlinx" -> KotlinxResponseMapperFactory(kotlinJsonProvider)
                else -> throw IllegalArgumentException("unknown provider: $providerName")
            }

            val builder = ApiClientFactory.builder()
                .config("https://jsonplaceholder.typicode.com") {
                    bodySerializer = createKotlinxBodySerializer(kotlinJsonProvider)
                }

            val configured = if (useExplicitFactory) {
                builder.responseMapperFactory(mapperFactory)
            } else {
                builder.addResponseMapperFactory(mapperFactory)
            }

            return configured.createClient(JsonPlaceholderApiClient::class)
        }

        @Test
        fun getPosts_returnsNonEmptyList() = runBlocking {
            for ((provider, explicit) in providerModes()) {
                val client = buildClient(provider, explicit)
                val posts = client.getPosts()
                assertTrue(posts.isNotEmpty(), "Expected non-empty posts for provider=$provider explicit=$explicit")
            }
        }

        @Test
        fun getPostById_returnsPost() = runBlocking {
            for ((provider, explicit) in providerModes()) {
                val client = buildClient(provider, explicit)
                val posts = client.getPosts()
                val firstId = posts.first().id.getOrNull()
                assertNotNull(firstId, "Expected first post to have an id for provider=$provider explicit=$explicit")
                val post = client.getPost(firstId)
                assertNotNull(post, "Expected getPost to return a post for provider=$provider explicit=$explicit")
            }
        }

        @Test
        fun getCommentsForPost_returnsList() = runBlocking {
            for ((provider, explicit) in providerModes()) {
                val client = buildClient(provider, explicit)
                val posts = client.getPosts()
                val firstId = posts.first().id.getOrNull()
                assertNotNull(firstId, "Expected first post to have an id for provider=$provider explicit=$explicit")
                val comments = client.getComments(firstId)
                assertTrue(comments.isNotEmpty(), "Expected comments for provider=$provider explicit=$explicit")
            }
        }

        @Test
        fun createPost_returnsCreatedPost() = runBlocking {
            for ((provider, explicit) in providerModes()) {
                val client = buildClient(provider, explicit)

                val newPost = Post(
                    userId = OptionalField.Present(1),
                    id = OptionalField.Absent,
                    title = OptionalField.Present("integration test title"),
                    body = OptionalField.Present("integration test body"),
                )

                val created = client.createPost(newPost)
                assertNotNull(created, "Expected created post for provider=$provider explicit=$explicit")
            }
        }
    }
}
