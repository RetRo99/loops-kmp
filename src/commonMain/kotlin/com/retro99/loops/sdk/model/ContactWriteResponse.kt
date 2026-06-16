package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactWriteResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("id")
    val id: String,
)
