package io.github.aeshen.restify.runtime.client.path.impl

import io.github.aeshen.restify.runtime.client.path.UrlBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object DefaultUrlBuilder : UrlBuilder {
    override fun build(
        baseUrl: String,
        pathTemplate: String,
        pathParams: Map<String, String>,
        queryParams: Map<String, String?>,
    ): String {
        // resolve path template like "/users/{id}" -> "/users/encodedId"
        var path = pathTemplate
        for ((k, v) in pathParams) {
            val encoded = URLEncoder.encode(v, StandardCharsets.UTF_8.toString())
            path = path.replace("{$k}", encoded)
        }
        // build query string
        val qs =
            queryParams.entries
                .filter { it.value != null }
                .joinToString(
                    "&",
                ) { (k, v) ->
                    "${URLEncoder.encode(
                        k,
                        "UTF-8",
                    )}=${URLEncoder.encode(v!!, "UTF-8")}"
                }.let {
                    if (it.isEmpty()) {
                        ""
                    } else {
                        "?$it"
                    }
                }
        return baseUrl.trimEnd('/') + path + qs
    }
}
