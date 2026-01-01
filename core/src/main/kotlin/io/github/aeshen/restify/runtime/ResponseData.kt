package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.annotation.http.MediaType

data class ResponseData(
    val statusCode: Int,
    val headers: Map<String, String>,
    // raw bytes; mappers deserialize as needed
    val body: ByteArray?,
    val contentType: MediaType? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as ResponseData

        if (statusCode != other.statusCode) {
            return false
        }
        if (headers != other.headers) {
            return false
        }
        if (!body.contentEquals(other.body)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}
