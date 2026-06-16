package com.retro99.loops.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * A JSON value carried by Loops custom contact properties, event properties, and transactional
 * data variables.
 *
 * The set of cases is a superset of every type the official Loops SDKs declare for these fields:
 * - Contact properties: `string | number | boolean | null`
 * - Event properties: `string | number | boolean`
 * - Transactional data variables: `string | number | Array<Record<string, string | number>>`
 *
 * Modelling the full JSON shape (including [ListValue]/[ObjectValue]/[NullValue]) makes decoding
 * **total**: any JSON value the API returns maps to a case, so [LoopsValueSerializer] never throws.
 * Build values with the [of] factories, e.g. `LoopsValue.of("pro")`, `LoopsValue.of(42)`.
 */
@Serializable(with = LoopsValueSerializer::class)
sealed class LoopsValue {

    data class StringValue(val value: String) : LoopsValue()

    data class NumberValue(val value: Double) : LoopsValue()

    data class BooleanValue(val value: Boolean) : LoopsValue()

    data class ListValue(val value: List<LoopsValue>) : LoopsValue()

    data class ObjectValue(val value: Map<String, LoopsValue>) : LoopsValue()

    data object NullValue : LoopsValue()

    companion object {
        fun of(value: String) = StringValue(value)
        fun of(value: Number) = NumberValue(value.toDouble())
        fun of(value: Boolean) = BooleanValue(value)
        fun of(value: List<LoopsValue>) = ListValue(value)
        fun of(value: Map<String, LoopsValue>) = ObjectValue(value)

        fun ofDateMillis(epochMilliseconds: Long) = NumberValue(epochMilliseconds.toDouble())
        fun ofDateString(iso8601: String) = StringValue(iso8601)
    }
}

object LoopsValueSerializer : KSerializer<LoopsValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LoopsValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LoopsValue) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("LoopsValue can only be serialized to JSON")
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): LoopsValue {
        val input = decoder as JsonDecoder
        return input.decodeJsonElement().toLoopsValue()
    }

    private fun LoopsValue.toJsonElement(): JsonElement =
        when (this) {
            is LoopsValue.StringValue -> JsonPrimitive(value)
            is LoopsValue.NumberValue -> JsonPrimitive(value)
            is LoopsValue.BooleanValue -> JsonPrimitive(value)
            is LoopsValue.ListValue -> buildJsonArray {
                value.forEach { add(it.toJsonElement()) }
            }
            is LoopsValue.ObjectValue -> buildJsonObject {
                value.forEach { (key, element) -> put(key, element.toJsonElement()) }
            }
            LoopsValue.NullValue -> JsonNull
        }

    private fun JsonElement.toLoopsValue(): LoopsValue =
        when (this) {
            is JsonNull -> LoopsValue.NullValue
            is JsonPrimitive -> when {
                isString -> LoopsValue.StringValue(content)
                booleanOrNull != null -> LoopsValue.BooleanValue(boolean)
                // Prefer Long when integral so 0 stays 0 (not 0.0), else fall back to Double.
                longOrNull != null -> LoopsValue.NumberValue(long.toDouble())
                doubleOrNull != null -> LoopsValue.NumberValue(double)
                // A lenient unquoted token that is neither bool nor number is treated as a string.
                else -> LoopsValue.StringValue(content)
            }
            is JsonArray -> LoopsValue.ListValue(map { it.toLoopsValue() })
            is JsonObject -> LoopsValue.ObjectValue(mapValues { (_, element) -> element.toLoopsValue() })
        }
}
