package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("source")
    val source: String? = null,
    @SerialName("subscribed")
    val subscribed: Boolean? = null,
    @SerialName("userGroup")
    val userGroup: String? = null,
    @SerialName("userId")
    val userId: String? = null,
    @SerialName("mailingLists")
    val mailingLists: Map<String, Boolean>? = null,
    @SerialName("optInStatus")
    val optInStatus: String? = null,
)
