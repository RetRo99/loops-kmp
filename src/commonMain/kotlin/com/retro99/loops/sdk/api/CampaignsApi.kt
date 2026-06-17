package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.Campaign
import com.retro99.loops.sdk.model.NameRequest
import com.retro99.loops.sdk.model.Page
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Campaigns API group.
 *
 * Accessed via [LoopsClient.campaigns]:
 * ```kotlin
 * val campaigns = client.campaigns.list()
 * ```
 */
@JvmAsync
class CampaignsApi internal constructor(
    internal val http: LoopsHttp,
) {

    suspend fun list(perPage: Int? = null, cursor: String? = null): Page<Campaign> =
        http.execute {
            get("campaigns") {
                perPage?.let { parameter("perPage", it) }
                cursor?.let { parameter("cursor", it) }
            }.body()
        }

    suspend fun get(id: String): Campaign =
        http.execute {
            get("campaigns/$id").body()
        }

    suspend fun create(request: NameRequest): Campaign =
        http.execute {
            post("campaigns") {
                setBody(request)
            }.body()
        }

    suspend fun update(id: String, request: NameRequest): Campaign =
        http.execute {
            post("campaigns/$id") {
                setBody(request)
            }.body()
        }
}
