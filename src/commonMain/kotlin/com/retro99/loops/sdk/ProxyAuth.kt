package com.retro99.loops.sdk

/**
 * How a proxy-mode [LoopsClient] authenticates to the consumer's own backend proxy.
 * This is never the Loops API key — the proxy holds that server-side.
 */
sealed interface ProxyAuth {

    /** No client auth (proxy is public, or authenticates out-of-band via cookies/mTLS). */
    data object None : ProxyAuth

    /**
     * Dynamic bearer token, re-evaluated on every request so a rotating session token is
     * picked up without rebuilding the client. Return null to omit the Authorization header.
     */
    fun interface BearerToken : ProxyAuth {
        suspend fun token(): String?
    }

    /** Arbitrary dynamic headers, re-evaluated on every request. */
    fun interface Headers : ProxyAuth {
        suspend fun headers(): Map<String, String>
    }
}
