package com.retro99.loops.sdk

import kotlinx.serialization.json.Json

internal val sdkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}
