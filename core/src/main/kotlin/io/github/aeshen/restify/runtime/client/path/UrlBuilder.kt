package io.github.aeshen.restify.runtime.client.path

interface UrlBuilder {
    fun build(
        baseUrl: String,
        pathTemplate: String,
        pathParams: Map<String, String>,
        queryParams: Map<String, String?>,
    ): String
}
