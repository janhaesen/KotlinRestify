package io.github.aeshen.restify.annotation.http

/**
 * Common media types to use from annotations like @Consumes / @Produces.
 * Use the enum value in annotations: `val value: MediaType = MediaType.APPLICATION_JSON`
 */
enum class MediaType(
    val value: String,
) {
    APPLICATION_JSON("application/json"),
    TEXT_PLAIN("text/plain"),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded"),
    MULTIPART_FORM_DATA("multipart/form-data"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    ANY("*/*"),
    ;

    override fun toString(): String = value
}
