package io.github.aeshen.restify.runtime.retry.impl

import io.github.aeshen.restify.runtime.DEFAULT_DELAY_MILLIS
import io.github.aeshen.restify.runtime.NANOS_PER_MILLISECOND
import io.github.aeshen.restify.runtime.retry.RetryPolicy
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

abstract class BaseRetryPolicy(
    private val timeoutMillis: Long,
    private val maxAttempts: Int = Int.MAX_VALUE
) : RetryPolicy {
    init {
        require(timeoutMillis > 0) { "timeoutMillis must be > 0" }
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }

    /**
     * Decide whether a throwable should be retried.
     * Subclasses override to provide predicate behavior.
     */
    protected open fun shouldRetry(t: Throwable): Boolean = true

    /**
     * Compute delay before next attempt (milliseconds).
     * Subclasses override to implement backoff strategies.
     */
    protected open fun computeDelayMillis(attempt: Int, last: Throwable?): Long = DEFAULT_DELAY_MILLIS

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    override suspend fun <T> retry(block: suspend () -> T): T {
        val deadlineNanos = System.nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
        var attempt = 0
        var lastThrowable: Throwable? = null

        while (++attempt <= maxAttempts) {
            // fail fast if the coroutine was cancelled
            currentCoroutineContext().ensureActive()

            if (System.nanoTime() > deadlineNanos) {
                throw lastThrowable
                    ?: IllegalStateException("Retry timed out without recorded exception")
            }

            try {
                return block()
            } catch (ce: CancellationException) {
                // propagate coroutine cancellation immediately
                throw ce
            } catch (t: Throwable) {
                lastThrowable = t
                if (!shouldRetry(t)) {
                    throw t
                }

                if (attempt >= maxAttempts) {
                    throw t
                }

                val remainingMillis = (deadlineNanos - System.nanoTime()).coerceAtLeast(0L) / NANOS_PER_MILLISECOND
                if (remainingMillis <= 0L) {
                    throw t
                }

                val toDelay = min(computeDelayMillis(attempt, t), remainingMillis)
                if (toDelay > 0L) {
                    // check cancellation before suspending
                    currentCoroutineContext().ensureActive()
                    delay(toDelay)
                }
                // continue loop
            }
        }

        throw lastThrowable
            ?: IllegalStateException("Retry failed without recorded exception")
    }
}
