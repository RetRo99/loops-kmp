package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionalMessage(
    @SerialName("id")
    val id: String,
    @SerialName("transactionalId")
    val transactionalId: String? = null,
    @SerialName("recipient")
    val recipient: String? = null,
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("sentAt")
    val sentAt: String? = null,
    @SerialName("openedAt")
    val openedAt: String? = null,
    @SerialName("clickedAt")
    val clickedAt: String? = null,
)
