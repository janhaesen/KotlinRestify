package io.github.aeshen.restify.runtime.retry.impl

import kotlin.math.pow
import kotlin.random.Random

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
    timeoutMillis: Long,
    private val maxAttemptsOverride: Int = 3,
    private val baseDelayMillis: Long = 100,
    private val multiplier: Double = 2.0,
    private val maxDelayMillis: Long = 10_000,
    private val jitterFactor: Double = 0.1,
    private val retryOn: (Throwable) -> Boolean = { true },
    private val random: Random = Random.Default,
) : BaseRetryPolicy(timeoutMillis, maxAttemptsOverride) {
    init {
        require(baseDelayMillis >= 0) { "baseDelayMillis must be >= 0" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(maxDelayMillis >= 0) { "maxDelayMillis must be >= 0" }
        require(jitterFactor >= 0.0) { "jitterFactor must be >= 0.0" }
    }

    override fun shouldRetry(t: Throwable): Boolean = retryOn(t)

    override fun computeDelayMillis(
        attempt: Int,
        last: Throwable?,
    ): Long {
        // attempt is 1-based (first retry after attempt == 1)
        val raw = (baseDelayMillis * multiplier.pow((attempt - 1).toDouble())).toLong()
        val capped = raw.coerceAtMost(maxDelayMillis)
        val jitter = ((random.nextDouble() * 2 - 1) * jitterFactor * capped).toLong()
        return (capped + jitter).coerceIn(0L, maxDelayMillis)
    }
}
