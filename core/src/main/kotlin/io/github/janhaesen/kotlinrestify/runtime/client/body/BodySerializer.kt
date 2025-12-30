package io.github.janhaesen.kotlinrestify.runtime.client.body

import io.github.aeshen.restify.annotation.http.MediaType

/**
 * Result of serializing a request body.
 * - payload: the actual object the HTTP client adapter will set as the request body (String, ByteArray, etc.)
 * - contentType: optional media type for the payload (e.g. "application/json")
 */
data class SerializedBody(
    val payload: Any?,
    val contentType: String?,
)

/**
 * Contract used by adapters to turn a high-level body into a transport payload.
 */
interface BodySerializer {
    fun serialize(
        body: Any,
        requestedContentType: MediaType?,
    ): SerializedBody
}
