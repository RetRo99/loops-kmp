package com.retro99.loops.sdk

/**
 * Controls automatic retrying of requests that fail with HTTP 429 (Too Many Requests).
 *
 * On a 429 the client waits and retries up to [maxRetries] times before giving up and
 * throwing [LoopsException.RateLimit]. The wait uses exponential backoff
 * ([initialBackoffMillis] doubling each attempt, capped at [maxBackoffMillis]). When the
 * 429 response carries a `Retry-After` header, that value takes precedence over the
 * computed backoff (still capped at [maxBackoffMillis]).
 *
 * Use [NONE] to disable retrying entirely (the previous SDK behaviour — 429 throws
 * immediately).
 *
 * @property maxRetries Number of retry attempts after the initial request. `0` disables
 *   retrying. Must not be negative.
 * @property initialBackoffMillis Backoff before the first retry, in milliseconds. Doubles
 *   on each subsequent attempt. Must be positive.
 * @property maxBackoffMillis Upper bound for any single backoff wait, in milliseconds.
 *   Must be greater than or equal to [initialBackoffMillis].
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialBackoffMillis: Long = 500,
    val maxBackoffMillis: Long = 10_000,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must not be negative, was $maxRetries" }
        require(initialBackoffMillis > 0) {
            "initialBackoffMillis must be positive, was $initialBackoffMillis"
        }
        require(maxBackoffMillis >= initialBackoffMillis) {
            "maxBackoffMillis ($maxBackoffMillis) must be >= initialBackoffMillis " +
                "($initialBackoffMillis)"
        }
    }

    /**
     * Backoff to wait before the retry at [attempt] (1-based), in milliseconds.
     * Exponential from [initialBackoffMillis], capped at [maxBackoffMillis]. A
     * non-null [retryAfterMillis] (parsed from a `Retry-After` header) overrides the
     * computed value, still capped at [maxBackoffMillis].
     */
    internal fun backoffMillis(attempt: Int, retryAfterMillis: Long?): Long {
        val computed = if (retryAfterMillis != null) {
            retryAfterMillis
        } else {
            // initial * 2^(attempt - 1), guarding against overflow on large attempts.
            val shift = (attempt - 1).coerceIn(0, 32)
            val factor = 1L shl shift
            if (initialBackoffMillis > maxBackoffMillis / factor) {
                maxBackoffMillis
            } else {
                initialBackoffMillis * factor
            }
        }
        return computed.coerceIn(0, maxBackoffMillis)
    }

    companion object {
        /** Sensible default: 3 retries, 500ms initial backoff, capped at 10s. */
        val DEFAULT: RetryConfig = RetryConfig()

        /** Disables automatic retrying; a 429 throws [LoopsException.RateLimit] immediately. */
        val NONE: RetryConfig = RetryConfig(maxRetries = 0)
    }
}
