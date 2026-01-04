package io.github.aeshen.example.api.json

import io.github.aeshen.example.api.json.JsonProviders.createObjectMapper
import io.github.aeshen.example.api.json.JsonProviders.kotlinJsonProvider
import io.github.aeshen.restify.generated.JsonPlaceholderApiClient
import io.github.aeshen.restify.runtime.ApiClientFactory
import io.github.aeshen.restify.runtime.serialization.OptionalField
import io.github.aeshen.restify.runtime.serialization.jackson.JacksonResponseMapperFactory
import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonDeserializer
import io.github.aeshen.restify.runtime.serialization.jackson.OptionalFieldJacksonSerializer
import io.github.aeshen.restify.runtime.serialization.kotlinx.KotlinxResponseMapperFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    fun createObjectMapper(): ObjectMapper =
        JsonMapper
            .builder()
            .addModule(
                KotlinModule
                    .Builder()
                    .enable(KotlinFeature.StrictNullChecks)
                    .build(),
            ) // register Kotlin support
            // be tolerant in tests
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // optional convenience
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .addModule(
                SimpleModule("OptionalFieldModule")
                    .addSerializer(OptionalField::class.java, OptionalFieldJacksonSerializer())
                    .addDeserializer(OptionalField::class.java, OptionalFieldJacksonDeserializer()),
            )
            .build()
}

class JsonPlaceholderApiClientIntegrationTest {
    companion object {
        @JvmStatic
        fun providerModes(): Stream<Arguments> =
            listOf(
                "jackson" to true,
                "jackson" to false,
                "kotlinx" to true,
                "kotlinx" to false,
            ).map { Arguments.of(it.first, it.second) }
                .stream()
    }

    private fun buildClient(
        providerName: String,
        useExplicitFactory: Boolean,
    ): JsonPlaceholderApiClient {
        val mapperFactory =
            when (providerName) {
                "jackson" -> JacksonResponseMapperFactory(createObjectMapper())
                "kotlinx" -> KotlinxResponseMapperFactory(kotlinJsonProvider)
                else -> throw IllegalArgumentException("unknown provider: $providerName")
            }

        println("Building client with provider=$providerName explicit=$useExplicitFactory")

        val builder =
            ApiClientFactory
                .builder()
                .config("https://jsonplaceholder.typicode.com")

        val configured =
            if (useExplicitFactory) {
                builder.responseMapperFactory(mapperFactory)
            } else {
                builder.addResponseMapperFactory(mapperFactory)
            }

        return configured.createClient(JsonPlaceholderApiClient::class)
    }

    @ParameterizedTest
    @MethodSource("providerModes")
    fun getPosts_returnsNonEmptyList(
        provider: String,
        explicit: Boolean,
    ) = runBlocking {
        val client = buildClient(provider, explicit)
        val posts = client.getPosts()
        assertTrue(
            posts.isNotEmpty(),
            "Expected non-empty posts for provider=$provider explicit=$explicit",
        )
    }

    @ParameterizedTest
    @MethodSource("providerModes")
    fun getPostById_returnsPost(
        provider: String,
        explicit: Boolean,
    ) = runBlocking {
        val client = buildClient(provider, explicit)
        val posts = client.getPosts()
        val firstId = posts.first().id.getOrNull()
        assertNotNull(
            firstId,
            "Expected first post to have an id for provider=$provider explicit=$explicit",
        )
        val post = client.getPost(firstId)
        assertNotNull(
            post,
            "Expected getPost to return a post for provider=$provider explicit=$explicit",
        )
    }

    @ParameterizedTest
    @MethodSource("providerModes")
    fun getCommentsForPost_returnsList(
        provider: String,
        explicit: Boolean,
    ) = runBlocking {
        val client = buildClient(provider, explicit)
        val posts = client.getPosts()
        val firstId = posts.first().id.getOrNull()
        assertNotNull(
            firstId,
            "Expected first post to have an id for provider=$provider explicit=$explicit",
        )
        val comments = client.getComments(firstId)
        assertTrue(
            comments.isNotEmpty(),
            "Expected comments for provider=$provider explicit=$explicit",
        )
    }

    @ParameterizedTest
    @MethodSource("providerModes")
    fun createPost_returnsCreatedPost(
        provider: String,
        explicit: Boolean,
    ) = runBlocking {
        val client = buildClient(provider, explicit)

        val newPost =
            Post(
                userId = OptionalField.Present(1),
                id = OptionalField.Absent,
                title = OptionalField.Present("integration test title"),
                body = OptionalField.Present("integration test body"),
            )

        val created = client.createPost(newPost)
        println(newPost)
        assertNotNull(
            created,
            "Expected created post for provider=$provider explicit=$explicit",
        )
    }
}
