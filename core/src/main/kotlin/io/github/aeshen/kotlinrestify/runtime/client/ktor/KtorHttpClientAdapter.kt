package io.github.aeshen.kotlinrestify.runtime.client.ktor

import io.github.aeshen.kotlinrestify.runtime.ApiConfig
import io.github.aeshen.kotlinrestify.runtime.RequestData
import io.github.aeshen.kotlinrestify.runtime.ResponseData
import io.github.aeshen.kotlinrestify.runtime.client.HttpClientAdapter
import io.github.aeshen.kotlinrestify.runtime.client.body.DefaultBodySerializer
import io.github.aeshen.kotlinrestify.runtime.client.body.SerializedBody
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
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import java.net.URLEncoder

class KtorHttpClientAdapter(
    private val client: HttpClient = defaultClient(),
) : HttpClientAdapter {
    companion object {
        private fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                install(HttpTimeout)
            }
    }

    override suspend fun execute(
        request: RequestData,
        config: ApiConfig,
    ): ResponseData {
        val fullUrl = buildUrl(config.baseUrl, request.urlPath, request.queryParameters)
        val mergedHeaders = config.defaultHeaders + request.headers

        // choose serializer: configured one or default
        val serializer = config.bodySerializer ?: DefaultBodySerializer
        val serialized =
            when (val bodyObj = request.body) {
                null -> SerializedBody(null, null)
                else -> serializer.serialize(bodyObj, request.contentType)
            }

        val httpResponse: HttpResponse =
            client.request {
                url(fullUrl)
                method = HttpMethod.parse(request.method.name)
                headers {
                    mergedHeaders.forEach { (k, v) -> append(k, v) }
                }
                timeout {
                    requestTimeoutMillis = config.timeoutMillis
                }

                when (val payload = serialized.payload) {
                    null -> { /* no body */ }

                    is ByteArray -> {
                        setBody(ByteArrayContent(payload))
                    }

                    is String -> {
                        val ct =
                            serialized.contentType
                                ?: request.contentType?.toString()
                                ?: "application/json"
                        setBody(TextContent(payload, ContentType.parse(ct)))
                    }

                    else -> {
                        error(
                            "Unsupported serialized payload type: ${payload::class}. BodySerializer must return " +
                                "String or ByteArray.",
                        )
                    }
                }
            }

        val respBytes = httpResponse.readRawBytes()
        val respHeaders = httpResponse.headers.entries().associate { it.key to it.value.joinToString(",") }
        return ResponseData(
            statusCode = httpResponse.status.value,
            headers = respHeaders,
            body =
                if (respBytes.isEmpty()) {
                    null
                } else {
                    respBytes
                },
        )
    }

    private fun buildUrl(
        base: String,
        path: String,
        query: Map<String, String>,
    ): String {
        val baseTrimmed =
            if (base.endsWith("/")) {
                base.dropLast(1)
            } else {
                base
            }
        val pathPrefix =
            if (path.startsWith("/")) {
                path
            } else {
                "/$path"
            }
        val sb = StringBuilder(baseTrimmed).append(pathPrefix)
        if (query.isNotEmpty()) {
            sb.append("?")
            sb.append(query.entries.joinToString("&") { (k, v) -> "${encodeComponent(k)}=${encodeComponent(v)}" })
        }
        return sb.toString()
    }

    private fun encodeComponent(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
}
