package io.github.aeshen.restify.runtime.client.adapter.ktor

import io.github.aeshen.restify.annotation.http.HttpMethod
import io.github.aeshen.restify.runtime.ApiConfig
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.adapter.HttpClientAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import java.util.concurrent.atomic.AtomicBoolean

class KtorHttpClientAdapter(
    private val client: HttpClient = defaultClient(),
) : HttpClientAdapter {
    companion object {
        private fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                install(HttpTimeout)
            }
    }

    private val closed = AtomicBoolean(false)

    override suspend fun execute(
        request: RequestData,
        config: ApiConfig,
    ): ResponseData {
        // *Assume* request.urlPath is already the full URL (AdapterHttpClient must build it).
        val fullUrl = request.urlPath

        // perform the HTTP request with Ktor using the provided full URL
        val response: HttpResponse =
            client.request {
                url(fullUrl)

                // map runtime HttpMethod -> Ktor HttpMethod
                method =
                    when (request.method) {
                        HttpMethod.GET -> io.ktor.http.HttpMethod.Get
                        HttpMethod.POST -> io.ktor.http.HttpMethod.Post
                        HttpMethod.PUT -> io.ktor.http.HttpMethod.Put
                        HttpMethod.DELETE -> io.ktor.http.HttpMethod.Delete
                        HttpMethod.PATCH -> io.ktor.http.HttpMethod.Patch
                        HttpMethod.HEAD -> io.ktor.http.HttpMethod.Head
                        HttpMethod.OPTIONS -> TODO()
                    }

                // merge config default headers first, then request headers override
                headers {
                    config.defaultHeaders.forEach { (k, v) -> append(k, v) }
                    request.headers.forEach { (k, v) -> append(k, v) }
                }

                // timeouts (if provided)
                config.timeoutMillis?.let {
                    // this@request.plugins.install(HttpTimeout) // ensure plugin available in client; safe no-op if already
                    timeout {
                        requestTimeoutMillis = it
                    }
                }

                // set body if present (adapter receives serialized payload)
                request.body?.let { payload ->
                    when (payload) {
                        is ByteArray -> {
                            setBody(ByteArrayContent(payload))
                        }

                        is String -> {
                            val ctHeader = request.headers["Content-Type"]
                            val ct =
                                ctHeader?.let { ContentType.parse(it) }
                                    ?: ContentType.Application.Json
                            setBody(TextContent(payload, ct))
                        }

                        else -> {
                            setBody(payload)
                        }
                    }
                }
            }

        val bytes = response.readRawBytes()
        val status = response.status.value
        val respHeaders =
            response.headers.entries().associate {
                it.key to
                    it.value.joinToString(",")
            }

        return ResponseData(
            statusCode = status,
            headers = respHeaders,
            body = bytes,
            contentType = null, // keep simple; mapping to runtime MediaType can be added if needed
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                client.close()
            } catch (_: Throwable) {
            }
        }
    }
}
