package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.EventRequest
import com.retro99.loops.sdk.model.SuccessResponse
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Events API group.
 *
 * Accessed via [LoopsClient.events]:
 * ```kotlin
 * val result = client.events.send(EventRequest(eventName = "signup", email = "a@b.com"))
 * ```
 */
@JvmAsync
class EventsApi internal constructor(
    internal val http: LoopsHttp,
) {

    /**
     * Send an event. Optionally provide an [idempotencyKey] (≤100 chars) to prevent duplicate
     * events — the header `Idempotency-Key` is set only when the parameter is non-null.
     */
    suspend fun send(
        request: EventRequest,
        idempotencyKey: String? = null,
    ): SuccessResponse =
        http.execute {
            post("events/send") {
                setBody(request)
                idempotencyKey?.let { header("Idempotency-Key", it) }
            }.body()
        }
}
