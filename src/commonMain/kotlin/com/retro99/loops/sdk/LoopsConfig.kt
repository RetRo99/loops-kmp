package com.retro99.loops.sdk

internal sealed interface LoopsConfig {
    val baseUrl: String
    val retry: RetryConfig

    data class Direct(
        val apiKey: String,
        override val baseUrl: String,
        override val retry: RetryConfig = RetryConfig.DEFAULT,
    ) : LoopsConfig

    data class Proxy(
        override val baseUrl: String,
        val auth: ProxyAuth,
        override val retry: RetryConfig = RetryConfig.DEFAULT,
    ) : LoopsConfig
}
