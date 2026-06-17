package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailMessage(
    @SerialName("id")
    val id: String,
    @SerialName("campaignId")
    val campaignId: String? = null,
    @SerialName("subject")
    val subject: String? = null,
    @SerialName("previewText")
    val previewText: String? = null,
    @SerialName("fromName")
    val fromName: String? = null,
    @SerialName("fromEmail")
    val fromEmail: String? = null,
    @SerialName("replyToEmail")
    val replyToEmail: String? = null,
    @SerialName("lmx")
    val lmx: String? = null,
    @SerialName("contentRevisionId")
    val contentRevisionId: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("warnings")
    val warnings: List<EmailMessageWarning>? = null,
)

@Serializable
data class EmailMessageWarning(
    @SerialName("rule")
    val rule: String? = null,
    @SerialName("severity")
    val severity: String? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("path")
    val path: String? = null,
)
