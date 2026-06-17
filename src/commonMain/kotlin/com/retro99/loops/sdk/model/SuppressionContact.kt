package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SuppressionContact(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("userId")
    val userId: String? = null,
)

@Serializable
data class RemovalQuota(
    @SerialName("limit")
    val limit: Int,
    @SerialName("remaining")
    val remaining: Int,
)

@Serializable
data class SuppressionStatusResponse(
    @SerialName("contact")
    val contact: SuppressionContact,
    @SerialName("isSuppressed")
    val isSuppressed: Boolean,
    @SerialName("removalQuota")
    val removalQuota: RemovalQuota,
)

@Serializable
data class SuppressionRemovalResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("message")
    val message: String? = null,
    @SerialName("removalQuota")
    val removalQuota: RemovalQuota,
)
