package com.retro99.loops.sdk

/**
 * Network timeouts applied to every request. Guards against a hung Loops endpoint blocking
 * a caller indefinitely — without these, behaviour falls back to each platform engine's
 * defaults, which for some engines means no request timeout at all.
 *
 * A `null` value leaves that particular timeout to the underlying engine. Use [NONE] to
 * disable all SDK-level timeouts.
 *
 * @property requestTimeoutMillis Whole-request timeout (send + receive). `null` to leave to
 *   the engine.
 * @property connectTimeoutMillis Time allowed to establish a connection. `null` to leave to
 *   the engine.
 * @property socketTimeoutMillis Maximum idle time between two data packets. `null` to leave
 *   to the engine.
 */
data class TimeoutConfig(
    val requestTimeoutMillis: Long? = 30_000,
    val connectTimeoutMillis: Long? = 10_000,
    val socketTimeoutMillis: Long? = 30_000,
) {
    init {
        requestTimeoutMillis?.let {
            require(it > 0) { "requestTimeoutMillis must be positive, was $it" }
        }
        connectTimeoutMillis?.let {
            require(it > 0) { "connectTimeoutMillis must be positive, was $it" }
        }
        socketTimeoutMillis?.let {
            require(it > 0) { "socketTimeoutMillis must be positive, was $it" }
        }
    }

    companion object {
        /** Sensible defaults: 30s request, 10s connect, 30s socket. */
        val DEFAULT: TimeoutConfig = TimeoutConfig()

        /** Disables all SDK-level timeouts; the engine's own defaults apply. */
        val NONE: TimeoutConfig = TimeoutConfig(
            requestTimeoutMillis = null,
            connectTimeoutMillis = null,
            socketTimeoutMillis = null,
        )
    }
}
