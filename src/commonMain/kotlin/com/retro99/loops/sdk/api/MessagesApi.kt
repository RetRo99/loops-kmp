package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.EmailMessage
import com.retro99.loops.sdk.model.Page
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

@JvmAsync
class MessagesApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(perPage: Int? = null, cursor: String? = null): Page<EmailMessage> =
        http.execute {
            get("messages") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    suspend fun get(id: String): EmailMessage =
        http.execute {
            get("messages/$id").body()
        }
}
