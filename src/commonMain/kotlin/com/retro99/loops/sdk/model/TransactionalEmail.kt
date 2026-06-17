package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A transactional email definition managed via the transactional-emails endpoints.
 *
 * Distinct from [TransactionalMessage] (a sent-message log entry): this models the reusable
 * transactional email template, including its draft/published email-message references.
 *
 * Transactional email **management** endpoints are marked alpha by Loops and subject to change.
 */
@Serializable
data class TransactionalEmail(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("draftEmailMessageId")
    val draftEmailMessageId: String? = null,
    @SerialName("draftEmailMessageContentRevisionId")
    val draftEmailMessageContentRevisionId: String? = null,
    @SerialName("publishedEmailMessageId")
    val publishedEmailMessageId: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("dataVariables")
    val dataVariables: List<String> = emptyList(),
)
