package io.github.aeshen.restify.runtime.retry.impl

/**
 * Default implementation that retries until success or until the provided time budget elapses.
 *
 * @param timeoutMillis total time allowed for all attempts (enforced via withTimeout)
 * @param delayMillis delay between attempts in milliseconds (simple fixed backoff)
 * @param maxAttempts optional maximum number of attempts (defaults to unlimited within the time budget)
 * @param retryOn predicate to decide whether a caught throwable should be retried
 */
class FixedDelayRetryPolicy(
    timeoutMillis: Long,
    private val delayMillis: Long = 100L,
    maxAttempts: Int = Int.MAX_VALUE,
    private val retryOn: (Throwable) -> Boolean = { true },
) : BaseRetryPolicy(timeoutMillis, maxAttempts) {
    override fun shouldRetry(t: Throwable): Boolean = retryOn(t)

    override fun computeDelayMillis(
        attempt: Int,
        last: Throwable?,
    ): Long = delayMillis
}
