package io.github.aeshen.restify.runtime.client.adapter.ktor

import io.github.aeshen.restify.annotation.http.HttpMethod
import io.github.aeshen.restify.annotation.http.MediaType
import io.github.aeshen.restify.runtime.ApiConfig
import io.github.aeshen.restify.runtime.RequestData
import io.github.aeshen.restify.runtime.ResponseData
import io.github.aeshen.restify.runtime.client.adapter.HttpClientAdapter
import io.github.aeshen.restify.runtime.client.adapter.mapContentType
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
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import java.util.concurrent.atomic.AtomicBoolean

internal class KtorHttpClientAdapter(
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
        val fullUrl = request.urlPath

        val response: HttpResponse =
            client.request {
                url(fullUrl)

                method = mapHttpMethod(request)

                headers {
                    request.headers.forEach { (k, v) -> append(k, v) }
                }

                config.timeoutMillis?.let {
                    timeout {
                        requestTimeoutMillis = it
                    }
                }

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
                it.key to it.value.joinToString(",")
            }

        return ResponseData(
            statusCode = status,
            headers = respHeaders,
            body = bytes,
            contentType = respHeaders[HttpHeaders.ContentType]?.let { mapContentType(it) },
        )
    }

    private fun mapHttpMethod(request: RequestData): io.ktor.http.HttpMethod =
        when (request.method) {
            HttpMethod.GET -> io.ktor.http.HttpMethod.Get
            HttpMethod.POST -> io.ktor.http.HttpMethod.Post
            HttpMethod.PUT -> io.ktor.http.HttpMethod.Put
            HttpMethod.DELETE -> io.ktor.http.HttpMethod.Delete
            HttpMethod.PATCH -> io.ktor.http.HttpMethod.Patch
            HttpMethod.HEAD -> io.ktor.http.HttpMethod.Head
            HttpMethod.OPTIONS -> throw UnsupportedOperationException()
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
