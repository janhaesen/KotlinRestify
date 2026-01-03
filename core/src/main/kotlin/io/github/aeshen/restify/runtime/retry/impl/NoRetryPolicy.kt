package io.github.aeshen.restify.runtime.retry.impl

import io.github.aeshen.restify.runtime.retry.RetryPolicy

class NoRetryPolicy : RetryPolicy {
    override suspend fun <T> retry(block: suspend () -> T): T = block()
}
