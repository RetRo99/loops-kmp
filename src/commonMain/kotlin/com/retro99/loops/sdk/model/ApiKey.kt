package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("teamName")
    val teamName: String? = null,
)
