package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.EmailMessage
import com.retro99.loops.sdk.model.UpdateEmailMessageRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Email Messages API group.
 *
 * Accessed via [LoopsClient.emailMessages]:
 * ```kotlin
 * val message = client.emailMessages.get("msg-id")
 * ```
 */
@JvmAsync
class EmailMessagesApi internal constructor(
    internal val http: LoopsHttp,
) {

    /** Fetches a single email message by id (GET email-messages/{id}). */
    suspend fun get(id: String): EmailMessage =
        http.execute {
            get("email-messages/$id").body()
        }

    /** Updates an email message (POST email-messages/{id}). */
    suspend fun update(id: String, request: UpdateEmailMessageRequest): EmailMessage =
        http.execute {
            post("email-messages/$id") {
                setBody(request)
            }.body()
        }
}
