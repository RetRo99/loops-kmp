package com.retro99.loops.sdk

/**
 * Base type for all errors surfaced by [LoopsClient]. No third-party (Ktor, Okio,
 * kotlinx.serialization) exceptions are exposed to consumers — everything is mapped
 * onto one of these subtypes.
 */
sealed class LoopsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * The server responded with a non-2xx status (e.g. invalid API key, validation
     * error, rate limit, 5xx). [body] is the raw response payload.
     */
    class Api(
        val statusCode: Int,
        val body: String,
    ) : LoopsException("Loops API error $statusCode: $body")

    /**
     * No response was received (DNS failure, timeout, offline). Typically retryable.
     */
    class Network(
        cause: Throwable,
    ) : LoopsException("Network error: ${cause.message}", cause)

    /**
     * A 2xx response was received but its body could not be parsed into the expected
     * model. Almost always indicates an SDK bug worth reporting.
     */
    class Serialization(
        cause: Throwable,
    ) : LoopsException("Failed to parse response: ${cause.message}", cause)

    /**
     * The server responded with HTTP 429 (Too Many Requests). [limit] is the total
     * number of requests allowed per window; [remaining] is how many are left.
     * Values default to 0 when the response does not include the expected headers.
     */
    class RateLimit(
        val limit: Int,
        val remaining: Int,
    ) : LoopsException("Rate limit exceeded: $remaining/$limit remaining")
}
