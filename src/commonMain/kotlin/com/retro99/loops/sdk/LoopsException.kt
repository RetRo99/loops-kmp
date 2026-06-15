package com.retro99.loops.sdk

class LoopsException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
