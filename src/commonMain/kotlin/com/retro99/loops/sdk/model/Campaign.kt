package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Campaign(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("emailMessageId")
    val emailMessageId: String? = null,
)
