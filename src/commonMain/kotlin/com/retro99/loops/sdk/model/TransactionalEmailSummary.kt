package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A transactional email as returned by the **deprecated** `GET transactional` list endpoint
 * (`TransactionalApi.list`). This is the minimal shape the legacy endpoint returns; the current
 * `GET transactional-emails` endpoint (`TransactionalApi.listEmails`) returns the richer
 * [TransactionalEmail] resource instead.
 *
 * Field set matches the spec's `TransactionalEmail` list-item schema (and the official `loops-js`
 * `TransactionalEmail` interface): `id`, `name`, `lastUpdated`, `dataVariables`.
 */
@Serializable
data class TransactionalEmailSummary(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("lastUpdated")
    val lastUpdated: String? = null,
    @SerialName("dataVariables")
    val dataVariables: List<String> = emptyList(),
)
