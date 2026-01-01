package io.github.aeshen.restify.runtime.client.body

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
 * Contract used by adapters to turn a high-level body into a transport payload,
 * and to convert a raw transport payload back into a high-level object.
 *
 * - `serialize` is used before sending to the low-level `HttpClientAdapter`.
 * - `deserialize` is used after receiving from the low-level `HttpClientAdapter`.
 */
interface BodySerializer {
    /**
     * Serialize a high-level object into a transport payload and an optional content type.
     * `body` may be null for requests with no body.
     *
     * @param body domain object to serialize (nullable)
     * @param requestedContentType optional desired media type (may be used to select serializer variant)
     * @return SerializedBody containing payload and content type to set on the request
     */
    fun serialize(
        body: Any?,
        requestedContentType: MediaType?,
    ): SerializedBody

    /**
     * Deserialize a raw transport payload (from the adapter) into a high-level object.
     *
     * @param rawPayload the payload produced by the low-level client (String, ByteArray, stream, etc.)
     * @param contentType the payload's media type (may be null)
     * @return domain-level object (or null) produced from the raw payload
     */
    fun deserialize(
        rawPayload: Any?,
        contentType: MediaType?,
    ): ByteArray?
}
