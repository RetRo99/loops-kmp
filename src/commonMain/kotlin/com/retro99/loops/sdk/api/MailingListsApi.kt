package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.MailingList
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Mailing Lists API group.
 *
 * Accessed via [LoopsClient.lists]:
 * ```kotlin
 * val lists = client.lists.list()
 * ```
 */
@JvmAsync
class MailingListsApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(): List<MailingList> =
        http.execute {
            get("lists").body()
        }
}
