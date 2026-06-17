package com.retro99.loops.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryConfigTest {

    @Test
    fun `backoff grows exponentially and caps at maxBackoffMillis`() {
        val config = RetryConfig(initialBackoffMillis = 500, maxBackoffMillis = 10_000)
        assertEquals(500, config.backoffMillis(attempt = 1, retryAfterMillis = null))
        assertEquals(1_000, config.backoffMillis(attempt = 2, retryAfterMillis = null))
        assertEquals(2_000, config.backoffMillis(attempt = 3, retryAfterMillis = null))
        assertEquals(4_000, config.backoffMillis(attempt = 4, retryAfterMillis = null))
        assertEquals(8_000, config.backoffMillis(attempt = 5, retryAfterMillis = null))
        // 16_000 would exceed the cap.
        assertEquals(10_000, config.backoffMillis(attempt = 6, retryAfterMillis = null))
    }

    @Test
    fun `large attempt does not overflow and stays capped`() {
        val config = RetryConfig(initialBackoffMillis = 500, maxBackoffMillis = 10_000)
        assertEquals(10_000, config.backoffMillis(attempt = 100, retryAfterMillis = null))
    }

    @Test
    fun `retryAfter overrides computed backoff but is still capped`() {
        val config = RetryConfig(initialBackoffMillis = 500, maxBackoffMillis = 10_000)
        assertEquals(3_000, config.backoffMillis(attempt = 1, retryAfterMillis = 3_000))
        assertEquals(10_000, config.backoffMillis(attempt = 1, retryAfterMillis = 30_000))
    }

    @Test
    fun `NONE disables retries`() {
        assertEquals(0, RetryConfig.NONE.maxRetries)
    }

    @Test
    fun `negative maxRetries is rejected`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(maxRetries = -1) }
    }

    @Test
    fun `non-positive initialBackoff is rejected`() {
        assertFailsWith<IllegalArgumentException> { RetryConfig(initialBackoffMillis = 0) }
    }

    @Test
    fun `maxBackoff below initialBackoff is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RetryConfig(initialBackoffMillis = 1_000, maxBackoffMillis = 500)
        }
    }
}
