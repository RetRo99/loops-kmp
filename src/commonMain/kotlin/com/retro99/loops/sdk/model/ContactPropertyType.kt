package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContactPropertyType {
    @SerialName("string")
    String,
    @SerialName("number")
    Number,
    @SerialName("boolean")
    Boolean,
    @SerialName("date")
    Date,
}
