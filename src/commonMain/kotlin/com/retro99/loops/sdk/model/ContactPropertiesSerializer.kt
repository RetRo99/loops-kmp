package com.retro99.loops.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal object ContactPropertiesSerializer {

    private val mapSerializer = MapSerializer(
        String.serializer(),
        LoopsValueSerializer,
    )

    fun merge(known: JsonObject, custom: Map<String, LoopsValue>, json: Json): JsonObject {
        if (custom.isEmpty()) return known
        val customObject = json.encodeToJsonElement(mapSerializer, custom).jsonObject
        return JsonObject(known.toMap() + customObject)
    }

    fun extract(
        full: JsonObject,
        knownKeys: Set<String>,
        json: Json,
    ): Map<String, LoopsValue> {
        val extra = full.filterKeys { it !in knownKeys }
        if (extra.isEmpty()) return emptyMap()
        return json.decodeFromJsonElement(mapSerializer, JsonObject(extra))
    }
}
