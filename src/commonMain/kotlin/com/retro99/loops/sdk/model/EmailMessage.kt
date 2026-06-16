package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailMessage(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("fromName")
    val fromName: String? = null,
    @SerialName("fromEmail")
    val fromEmail: String? = null,
    @SerialName("replyTo")
    val replyTo: String? = null,
    @SerialName("previewText")
    val previewText: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
)
