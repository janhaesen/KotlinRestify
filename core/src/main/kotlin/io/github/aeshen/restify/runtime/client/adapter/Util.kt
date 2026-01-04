package io.github.aeshen.restify.runtime.client.adapter

import io.github.aeshen.restify.annotation.http.MediaType

// New helper to normalize Content-Type header and map to MediaType
fun mapContentType(header: String?): MediaType? {
    if (header.isNullOrBlank()) {
        return null
    }

    // strip parameters like "; charset=utf-8"
    val base = header.substringBefore(';').trim()

    return try {
        MediaType.valueOf(base)
    } catch (_: Exception) {
        // fallback: try case-insensitive match against enum string representation
        try {
            MediaType
                .entries
                .firstOrNull {
                    it.toString().equals(base, ignoreCase = true)
                }
        } catch (_: Exception) {
            null
        }
    }
}
