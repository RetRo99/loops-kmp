package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NameRequest(
    @SerialName("name")
    val name: String,
)
