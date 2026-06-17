package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for `POST email-messages/{id}` (update an email message).
 *
 * [expectedRevisionId] is required for optimistic concurrency control; all other fields are
 * optional and only the set ones are sent on the wire (`encodeDefaults=false`).
 */
@Serializable
data class UpdateEmailMessageRequest(
    @SerialName("expectedRevisionId")
    val expectedRevisionId: String,
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
)
