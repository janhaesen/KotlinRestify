package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.annotation.http.HttpMethod
import io.github.aeshen.restify.annotation.http.MediaType

data class RequestData(
    val method: HttpMethod,
    // path template or full path; transport will resolve with baseUrl
    val urlPath: String,
    // template params (not URL-encoded yet)
    val pathParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    // allow nullable values; transport will drop nulls
    val queryParameters: Map<String, String?> = emptyMap(),
    val body: Any? = null,
    val contentType: MediaType? = null,
    val perRequestConfig: ApiConfig? = null,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        inline fun build(block: Builder.() -> Unit): RequestData = builder().apply(block).build()
    }

    @Suppress("TooManyFunctions")
    class Builder internal constructor() {
        private var method: HttpMethod? = null
        private var urlPath: String? = null
        private val pathParameters: MutableMap<String, String> = mutableMapOf()
        private val headers: MutableMap<String, String> = mutableMapOf()
        private val queryParameters: MutableMap<String, String?> = mutableMapOf()
        private var body: Any? = null
        private var contentType: MediaType? = null
        private var perRequestConfig: ApiConfig? = null

        fun method(method: HttpMethod) = apply { this.method = method }

        fun urlPath(urlPath: String) = apply { this.urlPath = urlPath }

        fun pathParam(
            name: String,
            value: String,
        ) = apply { pathParameters[name] = value }

        fun pathParameters(map: Map<String, String>) = apply { this.pathParameters.putAll(map) }

        fun header(
            name: String,
            value: String,
        ) = apply { headers[name] = value }

        fun headers(map: Map<String, String>) = apply { this.headers.putAll(map) }

        fun queryParam(
            name: String,
            value: String?,
        ) = apply { queryParameters[name] = value }

        fun queryParameters(map: Map<String, String?>) = apply { this.queryParameters.putAll(map) }

        fun body(body: Any?) = apply { this.body = body }

        fun contentType(contentType: MediaType?) = apply { this.contentType = contentType }

        fun perRequestConfig(perRequestConfig: ApiConfig?) =
            apply {
                this.perRequestConfig =
                    perRequestConfig
            }

        fun build(): RequestData {
            val m =
                method
                    ?: throw IllegalStateException("RequestData.method must be provided")
            val p =
                urlPath
                    ?: throw IllegalStateException("RequestData.urlPath must be provided")
            return RequestData(
                method = m,
                urlPath = p,
                pathParameters = pathParameters.toMap(),
                headers = headers.toMap(),
                queryParameters = queryParameters.toMap(),
                body = body,
                contentType = contentType,
                perRequestConfig = perRequestConfig,
            )
        }
    }
}
