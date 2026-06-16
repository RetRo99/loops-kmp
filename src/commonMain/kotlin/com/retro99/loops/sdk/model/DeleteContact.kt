package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteContactRequest(
    @SerialName("email")
    val email: String? = null,
    @SerialName("userId")
    val userId: String? = null,
)

@Serializable
data class DeleteResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("message")
    val message: String? = null,
)
