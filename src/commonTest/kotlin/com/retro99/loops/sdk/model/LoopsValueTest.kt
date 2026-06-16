package com.retro99.loops.sdk.model

import com.retro99.loops.sdk.sdkJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoopsValueTest {

    private val json = sdkJson

    @Test
    fun `LoopsValue StringValue serializes as JSON string primitive`() {
        val result = json.encodeToString(LoopsValueSerializer, LoopsValue.of("hello"))
        assertEquals("\"hello\"", result)
    }

    @Test
    fun `LoopsValue NumberValue serializes as JSON number primitive`() {
        val result = json.encodeToString(LoopsValueSerializer, LoopsValue.of(42))
        assertEquals("42.0", result)
    }

    @Test
    fun `LoopsValue BooleanValue serializes as JSON boolean primitive`() {
        val result = json.encodeToString(LoopsValueSerializer, LoopsValue.of(true))
        assertEquals("true", result)
    }

    @Test
    fun `LoopsValue deserializes string from JSON`() {
        val result = json.decodeFromString(LoopsValueSerializer, "\"world\"")
        assertIs<LoopsValue.StringValue>(result)
        assertEquals("world", result.value)
    }

    @Test
    fun `LoopsValue deserializes number from JSON`() {
        val result = json.decodeFromString(LoopsValueSerializer, "3.14")
        assertIs<LoopsValue.NumberValue>(result)
        assertEquals(3.14, result.value)
    }

    @Test
    fun `LoopsValue deserializes boolean from JSON`() {
        val result = json.decodeFromString(LoopsValueSerializer, "false")
        assertIs<LoopsValue.BooleanValue>(result)
        assertEquals(false, result.value)
    }

    @Test
    fun `ContactPropertiesSerializer merges custom properties into known object`() {
        val known = JsonObject(mapOf("email" to json.encodeToJsonElement("a@b.com")))
        val custom = mapOf(
            "plan" to LoopsValue.of("pro"),
            "score" to LoopsValue.of(100),
            "active" to LoopsValue.of(true),
        )
        val merged = ContactPropertiesSerializer.merge(known, custom, json)
        assertEquals(4, merged.size)
        assertEquals("\"pro\"", merged["plan"].toString())
        assertEquals("100.0", merged["score"].toString())
        assertEquals("true", merged["active"].toString())
    }

    @Test
    fun `ContactPropertiesSerializer extracts unknown keys into custom properties`() {
        val full = """{"email":"a@b.com","plan":"pro","score":100,"active":true}"""
        val parsed = json.decodeFromString<JsonObject>(full)
        val knownKeys = setOf("email")
        val extra = ContactPropertiesSerializer.extract(parsed, knownKeys, json)
        assertEquals(3, extra.size)
        assertIs<LoopsValue.StringValue>(extra["plan"])
        assertEquals("pro", (extra["plan"] as LoopsValue.StringValue).value)
        assertIs<LoopsValue.NumberValue>(extra["score"])
        assertEquals(100.0, (extra["score"] as LoopsValue.NumberValue).value)
        assertIs<LoopsValue.BooleanValue>(extra["active"])
        assertEquals(true, (extra["active"] as LoopsValue.BooleanValue).value)
    }

    @Test
    fun `ContactPropertiesSerializer merge with empty custom returns known object unchanged`() {
        val known = JsonObject(mapOf("email" to json.encodeToJsonElement("a@b.com")))
        val merged = ContactPropertiesSerializer.merge(known, emptyMap(), json)
        assertEquals(1, merged.size)
        assertTrue(merged.containsKey("email"))
    }

    @Test
    fun `ContactPropertiesSerializer extract with no extra keys returns empty map`() {
        val full = """{"email":"a@b.com"}"""
        val parsed = json.decodeFromString<JsonObject>(full)
        val extra = ContactPropertiesSerializer.extract(parsed, setOf("email"), json)
        assertTrue(extra.isEmpty())
    }

    @Test
    fun `heavy JSON with 1000 custom properties round-trips correctly`() {
        val custom = buildMap {
            for (i in 0 until 1000) {
                put("str_$i", LoopsValue.of("value_$i"))
                put("num_$i", LoopsValue.of(i))
                put("bool_$i", LoopsValue.of(i % 2 == 0))
            }
        }

        val known = JsonObject(mapOf("email" to json.encodeToJsonElement("heavy@test.com")))
        val merged = ContactPropertiesSerializer.merge(known, custom, json)

        assertEquals(3001, merged.size) // 1000 str + 1000 num + 1000 bool + 1 email
        assertEquals("\"heavy@test.com\"", merged["email"].toString())

        val extracted = ContactPropertiesSerializer.extract(merged, setOf("email"), json)
        assertEquals(3000, extracted.size)

        for (i in 0 until 1000) {
            val strVal = extracted["str_$i"] as LoopsValue.StringValue
            assertEquals("value_$i", strVal.value)

            val numVal = extracted["num_$i"] as LoopsValue.NumberValue
            assertEquals(i.toDouble(), numVal.value)

            val boolVal = extracted["bool_$i"] as LoopsValue.BooleanValue
            assertEquals(i % 2 == 0, boolVal.value)
        }
    }

    @Test
    fun `heavy JSON round-trips through full encode-decode cycle`() {
        val custom = buildMap {
            for (i in 0 until 100) {
                put("prop_$i", when (i % 3) {
                    0 -> LoopsValue.of("string_$i")
                    1 -> LoopsValue.of(i * 1.5)
                    else -> LoopsValue.of(i % 2 == 0)
                })
            }
        }
        val known = JsonObject(mapOf("email" to json.encodeToJsonElement("cycle@test.com")))
        val merged = ContactPropertiesSerializer.merge(known, custom, json)
        val extracted = ContactPropertiesSerializer.extract(merged, setOf("email"), json)

        assertEquals(custom.size, extracted.size)
        for ((key, value) in custom) {
            val recovered = extracted[key]!!
            assertEquals(value::class, recovered::class)
            when (value) {
                is LoopsValue.StringValue -> assertEquals(
                    value.value, (recovered as LoopsValue.StringValue).value,
                )
                is LoopsValue.NumberValue -> assertEquals(
                    value.value, (recovered as LoopsValue.NumberValue).value,
                )
                is LoopsValue.BooleanValue -> assertEquals(
                    value.value, (recovered as LoopsValue.BooleanValue).value,
                )
                else -> assertEquals(value, recovered)
            }
        }
    }

    @Test
    fun `extract survives super complicated JSON with extra fields`() {
        val veryLong = "x".repeat(10000)
        val parsed = JsonObject(
            mapOf(
                "id" to JsonPrimitive("contact_123"),
                "email" to JsonPrimitive("alice@example.com"),
                "firstName" to JsonPrimitive("Alice"),
                "lastName" to JsonPrimitive(""),
                "unsubscribed" to JsonPrimitive(false),
                "score" to JsonPrimitive(0),
                "negative" to JsonPrimitive(-42.5),
                "large" to JsonPrimitive(9.9e99),
                "fraction" to JsonPrimitive(0.0000001),
                "emptyString" to JsonPrimitive(""),
                "emoji" to JsonPrimitive("Hello 👋 世界 🌍"),
                "specialChars" to JsonPrimitive("tab\tnewline\n\"quoted\" \\backslash"),
                "veryLongString" to JsonPrimitive(veryLong),
            ),
        )
        val knownKeys = setOf("id", "email", "firstName", "lastName")
        val extra = ContactPropertiesSerializer.extract(parsed, knownKeys, json)

        assertEquals(9, extra.size)
        assertIs<LoopsValue.BooleanValue>(extra["unsubscribed"])
        assertEquals(false, (extra["unsubscribed"] as LoopsValue.BooleanValue).value)
        assertIs<LoopsValue.NumberValue>(extra["score"])
        assertEquals(0.0, (extra["score"] as LoopsValue.NumberValue).value)
        assertIs<LoopsValue.NumberValue>(extra["negative"])
        assertEquals(-42.5, (extra["negative"] as LoopsValue.NumberValue).value)
        assertIs<LoopsValue.NumberValue>(extra["fraction"])
        assertEquals(1.0e-7, (extra["fraction"] as LoopsValue.NumberValue).value)
        assertIs<LoopsValue.StringValue>(extra["emoji"])
        assertEquals("Hello 👋 世界 🌍", (extra["emoji"] as LoopsValue.StringValue).value)
        assertIs<LoopsValue.StringValue>(extra["specialChars"])
        assertEquals("tab\tnewline\n\"quoted\" \\backslash", (extra["specialChars"] as LoopsValue.StringValue).value)
        assertIs<LoopsValue.StringValue>(extra["emptyString"])
        assertEquals("", (extra["emptyString"] as LoopsValue.StringValue).value)
        assertIs<LoopsValue.StringValue>(extra["veryLongString"])
        assertEquals(10000, (extra["veryLongString"] as LoopsValue.StringValue).value.length)
    }

    @Test
    fun `merge and extract round-trip preserves all field types`() {
        val known = JsonObject(mapOf(
            "email" to json.encodeToJsonElement("roundtrip@test.com"),
            "firstName" to json.encodeToJsonElement("Bob"),
        ))
        val custom = mapOf(
            "score" to LoopsValue.of(99.9),
            "tags" to LoopsValue.of("premium,beta"),
            "active" to LoopsValue.of(false),
        )
        val merged = ContactPropertiesSerializer.merge(known, custom, json)
        val extracted = ContactPropertiesSerializer.extract(merged, setOf("email", "firstName"), json)

        assertEquals(3, extracted.size)
        assertIs<LoopsValue.NumberValue>(extracted["score"])
        assertEquals(99.9, (extracted["score"] as LoopsValue.NumberValue).value)
        assertIs<LoopsValue.StringValue>(extracted["tags"])
        assertEquals("premium,beta", (extracted["tags"] as LoopsValue.StringValue).value)
        assertIs<LoopsValue.BooleanValue>(extracted["active"])
        assertEquals(false, (extracted["active"] as LoopsValue.BooleanValue).value)
    }

    // ── null / list / object cases (official types: contact props allow null;
    //    transactional dataVariables allow Array<Record<string, string|number>>) ──

    @Test
    fun `LoopsValue NullValue round-trips through JSON null`() {
        val encoded = json.encodeToString(LoopsValueSerializer, LoopsValue.NullValue)
        assertEquals("null", encoded)
        val decoded = json.decodeFromString(LoopsValueSerializer, "null")
        assertEquals(LoopsValue.NullValue, decoded)
    }

    @Test
    fun `LoopsValue decodes a JSON array into ListValue instead of throwing`() {
        val decoded = json.decodeFromString(LoopsValueSerializer, """["a", 1, true]""")
        assertIs<LoopsValue.ListValue>(decoded)
        assertEquals(3, decoded.value.size)
        assertEquals(LoopsValue.StringValue("a"), decoded.value[0])
        assertEquals(LoopsValue.NumberValue(1.0), decoded.value[1])
        assertEquals(LoopsValue.BooleanValue(true), decoded.value[2])
    }

    @Test
    fun `LoopsValue decodes a JSON object into ObjectValue instead of throwing`() {
        val decoded = json.decodeFromString(LoopsValueSerializer, """{"k":"v","n":7}""")
        assertIs<LoopsValue.ObjectValue>(decoded)
        assertEquals(LoopsValue.StringValue("v"), decoded.value["k"])
        assertEquals(LoopsValue.NumberValue(7.0), decoded.value["n"])
    }

    @Test
    fun `dataVariables array-of-objects shape round-trips losslessly`() {
        // Mirrors the official TransactionalVariables type:
        // Record<string, string | number | Array<Record<string, string | number>>>
        val items = LoopsValue.of(
            listOf(
                LoopsValue.of(mapOf("name" to LoopsValue.of("Widget"), "qty" to LoopsValue.of(2))),
                LoopsValue.of(mapOf("name" to LoopsValue.of("Gadget"), "qty" to LoopsValue.of(1))),
            ),
        )
        val encoded = json.encodeToString(LoopsValueSerializer, items)
        val decoded = json.decodeFromString(LoopsValueSerializer, encoded)
        assertEquals(items, decoded)
    }

    @Test
    fun `nested null inside list and object is preserved`() {
        val value = LoopsValue.of(
            mapOf(
                "maybe" to LoopsValue.NullValue,
                "items" to LoopsValue.of(listOf(LoopsValue.NullValue, LoopsValue.of("x"))),
            ),
        )
        val decoded = json.decodeFromString(
            LoopsValueSerializer,
            json.encodeToString(LoopsValueSerializer, value),
        )
        assertEquals(value, decoded)
    }
}
