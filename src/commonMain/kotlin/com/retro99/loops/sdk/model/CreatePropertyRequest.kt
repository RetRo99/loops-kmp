package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreatePropertyRequest(
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: ContactPropertyType,
)
