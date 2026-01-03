package io.github.aeshen.restify.runtime.client.body

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.io.readBytes

internal object PayloadUtils {
    /**
     * Convert a raw transport payload into a ByteArray or return null when input is null.
     * Handles ByteArray, String, InputStream, ByteBuffer and falls back to toString().
     */
    fun toByteArray(raw: Any?): ByteArray? {
        if (raw == null) {
            return null
        }

        return when (raw) {
            is ByteArray -> {
                raw
            }

            is String -> {
                raw.toByteArray(Charsets.UTF_8)
            }

            is InputStream -> {
                raw.readBytes()
            }

            is ByteBuffer -> {
                val dst = ByteArray(raw.remaining())
                val copy = raw.duplicate()
                copy.get(dst)
                dst
            }

            else -> {
                raw.toString().toByteArray(Charsets.UTF_8)
            }
        }
    }

    /** Return given bytes or an empty ByteArray when null. */
    fun bytesOrEmpty(bytes: ByteArray?): ByteArray = bytes ?: ByteArray(0)

    /**
     * Decode bytes to String using provided charset.
     * Throws IllegalStateException when bytes are null (preserves previous behavior that
     * mappers expect a present body).
     */
    fun bytesToString(
        bytes: ByteArray?,
        charset: Charset = Charsets.UTF_8,
    ): String {
        if (bytes == null) {
            throw IllegalStateException("Response body is not present")
        }

        return String(bytes, charset)
    }
}
