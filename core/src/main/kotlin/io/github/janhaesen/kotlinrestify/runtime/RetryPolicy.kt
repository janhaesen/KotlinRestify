package io.github.janhaesen.kotlinrestify.runtime

/**
 * Retry policy contract used by GeneratedApiCaller.
 */
interface RetryPolicy {
    suspend fun <T> retry(block: suspend () -> T): T
}
