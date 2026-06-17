package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.NameRequest
import com.retro99.loops.sdk.model.Page
import com.retro99.loops.sdk.model.SuccessResponse
import com.retro99.loops.sdk.model.TransactionalEmail
import com.retro99.loops.sdk.model.TransactionalEmailSummary
import com.retro99.loops.sdk.model.TransactionalSendRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Transactional Emails API group.
 *
 * Accessed via [LoopsClient.transactional]:
 * ```kotlin
 * val result = client.transactional.send(TransactionalSendRequest(email = "a@b.com", transactionalId = "t-1"))
 * ```
 */
@JvmAsync
class TransactionalApi internal constructor(
    internal val http: LoopsHttp,
) {

    /**
     * List transactional emails with optional pagination (GET transactional).
     *
     * This hits the **deprecated** `v1/transactional` list endpoint (matching the official
     * `loops-js` behavior) and returns minimal [TransactionalEmailSummary] entries. Prefer
     * [listEmails], which hits the current `v1/transactional-emails` endpoint and returns the
     * richer [TransactionalEmail] resource.
     */
    @Deprecated(
        message = "Use listEmails(), which hits the current v1/transactional-emails endpoint.",
        replaceWith = ReplaceWith("listEmails(perPage, cursor)"),
    )
    suspend fun list(
        perPage: Int? = null,
        cursor: String? = null,
    ): Page<TransactionalEmailSummary> =
        http.execute {
            get("transactional") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /**
     * List transactional email definitions with optional pagination
     * (GET transactional-emails). Returns the full [TransactionalEmail] resource, including
     * draft/published message references and timestamps.
     */
    suspend fun listEmails(
        perPage: Int? = null,
        cursor: String? = null,
    ): Page<TransactionalEmail> =
        http.execute {
            get("transactional-emails") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    /**
     * Send a transactional email. Optionally provide an [idempotencyKey] (≤100 chars) to
     * prevent duplicate sends — the header `Idempotency-Key` is set only when non-null.
     */
    suspend fun send(
        request: TransactionalSendRequest,
        idempotencyKey: String? = null,
    ): SuccessResponse =
        http.execute {
            post("transactional") {
                setBody(request)
                idempotencyKey?.let { header("Idempotency-Key", it) }
            }.body()
        }

    // region Transactional email management (alpha — subject to change by Loops)

    /**
     * Create a new transactional email definition (POST transactional-emails). The returned
     * [TransactionalEmail] has no draft/published message yet.
     */
    suspend fun createEmail(request: NameRequest): TransactionalEmail =
        http.execute {
            post("transactional-emails") {
                setBody(request)
            }.body()
        }

    /**
     * Get a transactional email definition by id (GET transactional-emails/{id}).
     */
    suspend fun getEmail(id: String): TransactionalEmail =
        http.execute {
            get("transactional-emails/$id").body()
        }

    /**
     * Update a transactional email's metadata (POST transactional-emails/{id}).
     */
    suspend fun updateEmail(id: String, request: NameRequest): TransactionalEmail =
        http.execute {
            post("transactional-emails/$id") {
                setBody(request)
            }.body()
        }

    /**
     * Ensure the transactional email has a draft email message, creating one if needed
     * (POST transactional-emails/{id}/draft). No request body.
     */
    suspend fun ensureEmailDraft(id: String): TransactionalEmail =
        http.execute {
            post("transactional-emails/$id/draft").body()
        }

    /**
     * Publish the transactional email's current draft email message
     * (POST transactional-emails/{id}/publish). No request body.
     */
    suspend fun publishEmail(id: String): TransactionalEmail =
        http.execute {
            post("transactional-emails/$id/publish").body()
        }

    // endregion
}
