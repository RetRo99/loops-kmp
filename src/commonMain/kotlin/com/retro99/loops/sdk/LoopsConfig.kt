package com.retro99.loops.sdk

internal sealed interface LoopsConfig {
    val baseUrl: String
    val retry: RetryConfig
    val timeout: TimeoutConfig
    val logging: LoggingConfig

    data class Direct(
        val apiKey: String,
        override val baseUrl: String,
        override val retry: RetryConfig = RetryConfig.DEFAULT,
        override val timeout: TimeoutConfig = TimeoutConfig.NONE,
        override val logging: LoggingConfig = LoggingConfig.DEFAULT,
    ) : LoopsConfig

    data class Proxy(
        override val baseUrl: String,
        val auth: ProxyAuth,
        override val retry: RetryConfig = RetryConfig.DEFAULT,
        override val timeout: TimeoutConfig = TimeoutConfig.NONE,
        override val logging: LoggingConfig = LoggingConfig.DEFAULT,
    ) : LoopsConfig
}
