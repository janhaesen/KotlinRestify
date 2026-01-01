package io.github.aeshen.example.api.json

import io.github.aeshen.restify.generated.JsonPlaceholderApiClient
import io.github.aeshen.restify.runtime.ApiClientFactory
import io.github.aeshen.restify.runtime.createClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonPlaceholderApiClientIntegrationTest {
    @Test
    fun `e2e getPosts and getPost`() =
        runBlocking {
            // instantiate generated client wired to the real JSONPlaceholder service
            val client =
                ApiClientFactory
                    .createClient<JsonPlaceholderApiClient> {
                        baseUrl = "https://jsonplaceholder.typicode.com"
                    }

            // getPosts should return a non-empty list and items should contain ids
            val posts = client.getPosts()
            assertTrue(posts.isNotEmpty(), "expected non-empty posts list")
            assertTrue(posts.any { it.id.isPresent }, "expected at least one post with id present")

            // getPost should return the post with id=1 and the Optional id should be present and equal to 1
            val post = client.getPost(1)
            assertNotNull(post, "expected getPost(1) to return a post")
            assertTrue(post.id.isPresent, "expected post.id to be present")
            assertEquals(1, post.id.get())
        }
}
