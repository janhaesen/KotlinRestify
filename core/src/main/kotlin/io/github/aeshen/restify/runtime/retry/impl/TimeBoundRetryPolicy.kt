package io.github.aeshen.restify.runtime.retry.impl

import io.github.aeshen.restify.runtime.retry.RetryPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Default implementation that retries until success or until the provided time budget elapses.
 *
 * @param timeoutMillis total time allowed for all attempts (enforced via withTimeout)
 * @param delayMillis delay between attempts in milliseconds (simple fixed backoff)
 * @param maxAttempts optional maximum number of attempts (defaults to unlimited within the time budget)
 * @param retryOn predicate to decide whether a caught throwable should be retried
 */
class TimeBoundRetryPolicy(
    private val timeoutMillis: Long,
    private val delayMillis: Long = 100L,
    private val maxAttempts: Int = Int.MAX_VALUE,
    private val retryOn: (Throwable) -> Boolean = { true },
) : RetryPolicy {
    init {
        require(timeoutMillis > 0) { "timeoutMillis must be > 0" }
        require(delayMillis >= 0) { "delayMillis must be >= 0" }
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    override suspend fun <T> retry(block: suspend () -> T): T {
        return withTimeout(timeoutMillis) {
            var attempt = 0
            var lastThrowable: Throwable? = null

            while (++attempt <= maxAttempts) {
                try {
                    return@withTimeout block()
                } catch (t: Throwable) {
                    lastThrowable = t
                    if (!retryOn(t)) {
                        throw t
                    }
                    if (attempt >= maxAttempts) {
                        throw t
                    }
                    if (delayMillis > 0) {
                        delay(delayMillis)
                    }
                    // loop and try again (overall time bounded by withTimeout)
                }
            }

            // should be unreachable because loop either returns or throws, but keep a safe fallback
            throw lastThrowable ?: IllegalStateException("Retry failed without recorded exception")
        }
    }
}
