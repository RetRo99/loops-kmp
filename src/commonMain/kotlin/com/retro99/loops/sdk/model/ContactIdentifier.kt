package com.retro99.loops.sdk.model

sealed class ContactIdentifier {
    data class ByEmail(val email: String) : ContactIdentifier()
    data class ByUserId(val userId: String) : ContactIdentifier()
}
