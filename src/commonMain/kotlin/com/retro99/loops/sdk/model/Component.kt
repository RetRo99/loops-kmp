package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Component(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    /** The component body serialized as LMX. */
    @SerialName("lmx")
    val lmx: String? = null,
)
