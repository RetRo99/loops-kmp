package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactProperty(
    @SerialName("key")
    val key: String,
    @SerialName("label")
    val label: String,
    @SerialName("type")
    val type: String,
)
