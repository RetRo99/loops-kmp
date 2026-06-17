package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pagination(
    @SerialName("totalResults")
    val totalResults: Int? = null,
    @SerialName("returnedResults")
    val returnedResults: Int? = null,
    @SerialName("perPage")
    val perPage: Int? = null,
    @SerialName("totalPages")
    val totalPages: Int? = null,
    @SerialName("nextCursor")
    val nextCursor: String? = null,
    @SerialName("nextPage")
    val nextPage: String? = null,
)

@Serializable
data class Page<T>(
    val pagination: Pagination,
    val data: List<T>,
)
