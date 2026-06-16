package com.retro99.loops.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

@Serializable(with = LoopsValueSerializer::class)
sealed class LoopsValue {

    data class StringValue(val value: String) : LoopsValue()

    data class NumberValue(val value: Double) : LoopsValue()

    data class BooleanValue(val value: Boolean) : LoopsValue()

    companion object {
        fun of(value: String) = StringValue(value)
        fun of(value: Number) = NumberValue(value.toDouble())
        fun of(value: Boolean) = BooleanValue(value)
    }
}

object LoopsValueSerializer : KSerializer<LoopsValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LoopsValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LoopsValue) {
        when (value) {
            is LoopsValue.StringValue -> encoder.encodeString(value.value)
            is LoopsValue.NumberValue -> encoder.encodeDouble(value.value)
            is LoopsValue.BooleanValue -> encoder.encodeBoolean(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): LoopsValue {
        val input = decoder as JsonDecoder
        val element = input.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> LoopsValue.StringValue(element.content)
                element.booleanOrNull != null -> LoopsValue.BooleanValue(element.boolean)
                // Prefer Long when the number is integral so we don't widen e.g. 0 to "0.0",
                // then fall back to Double for fractional/exponent values.
                element.longOrNull != null -> LoopsValue.NumberValue(element.long.toDouble())
                element.doubleOrNull != null -> LoopsValue.NumberValue(element.double)
                // A lenient unquoted token that is neither bool nor number is treated as a string.
                else -> LoopsValue.StringValue(element.content)
            }
            else -> throw SerializationException(
                "Expected a JSON primitive (string, number, or boolean)",
            )
        }
    }
}
