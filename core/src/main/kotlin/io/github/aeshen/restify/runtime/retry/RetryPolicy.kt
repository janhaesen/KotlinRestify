package io.github.aeshen.restify.runtime.retry

/**
 * Retry policy contract used by ApiCaller.
 */
interface RetryPolicy {
    suspend fun <T> retry(block: suspend () -> T): T
}
