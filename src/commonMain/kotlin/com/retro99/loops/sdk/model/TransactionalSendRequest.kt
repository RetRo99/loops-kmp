package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    @SerialName("filename")
    val filename: String,
    @SerialName("contentType")
    val contentType: String,
    @SerialName("data")
    val data: String,
)

@Serializable
data class TransactionalSendRequest(
    @SerialName("email")
    val email: String,
    @SerialName("transactionalId")
    val transactionalId: String,
    @SerialName("addToAudience")
    val addToAudience: Boolean? = null,
    @SerialName("dataVariables")
    val dataVariables: Map<String, LoopsValue>? = null,
    @SerialName("attachments")
    val attachments: List<Attachment>? = null,
)
