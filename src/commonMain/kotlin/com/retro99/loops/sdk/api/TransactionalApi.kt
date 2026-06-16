package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.SuccessResponse
import com.retro99.loops.sdk.model.TransactionalSendRequest
import io.ktor.client.call.body
import io.ktor.client.request.header
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
}
