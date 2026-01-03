package io.github.aeshen.restify.runtime.retry.impl

import io.github.aeshen.restify.runtime.retry.RetryPolicy
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Pure-kotlin coroutine retry policy with exponential backoff and jitter.
 *
 * - maxAttempts: total attempts (including first)
 * - baseDelayMillis: initial delay before first retry
 * - multiplier: how the delay grows each retry (exponential)
 * - maxDelayMillis: cap on computed delay
 * - jitterFactor: relative jitter magnitude (0.0 = none, 0.1 = ±10%)
 */
class ExponentialBackoffRetryPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 100,
    private val multiplier: Double = 2.0,
    private val maxDelayMillis: Long = 10_000,
    private val jitterFactor: Double = 0.1,
    private val random: Random = Random.Default,
) : RetryPolicy {
    init {
        require(maxAttempts >= 1)
        require(baseDelayMillis >= 0)
        require(multiplier >= 1.0)
        require(jitterFactor >= 0.0)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> retry(block: suspend () -> T): T {
        var attempt = 0
        val delayMillis = baseDelayMillis.coerceAtLeast(0L)

        while (true) {
            // respect coroutine cancellation before attempt
            currentCoroutineContext().ensureActive()
            try {
                return block()
            } catch (ce: CancellationException) {
                // preserve cancellation
                throw ce
            } catch (ex: Exception) {
                attempt++
                if (attempt >= maxAttempts) {
                    throw ex
                }

                // compute jittered delay: base * multiplier^(attempt-1) with ±jitterFactor
                val raw = (delayMillis * Math.pow(multiplier, (attempt - 1).toDouble())).toLong()
                val capped = raw.coerceAtMost(maxDelayMillis)
                val jitter = ((random.nextDouble() * 2 - 1) * jitterFactor * capped).toLong()
                val sleep = (capped + jitter).coerceIn(0L, maxDelayMillis)

                // ensure cancellation during backoff wait
                currentCoroutineContext().ensureActive()
                delay(sleep)

                // continue to next attempt
            }
        }
    }
}
