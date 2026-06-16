package com.retro99.loops.sdk

internal sealed interface LoopsConfig {
    val baseUrl: String

    data class Direct(val apiKey: String, override val baseUrl: String) : LoopsConfig
    data class Proxy(override val baseUrl: String, val auth: ProxyAuth) : LoopsConfig
}
