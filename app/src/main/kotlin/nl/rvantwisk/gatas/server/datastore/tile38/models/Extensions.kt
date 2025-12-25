package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray


val json = Json { ignoreUnknownKeys = true }

/**
 * Transform a Tile38NearbyResult to a List of objects on what it contains
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38NearbyResult.fromJsonField(fieldName: String = "json"): List<T> {
    val jsonIdx = this.fields.indexOf(fieldName)
    if (jsonIdx == -1) return emptyList()

    return this.objects.mapNotNull { obj ->
        try {
            obj["fields"]?.jsonArray?.get(jsonIdx)?.let {
                json.decodeFromJsonElement<T>(it)
            }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38GetResult.fromJsonField(): T? {
    val jsonElement = this.fields?.get("json") ?: return null
    return try {
        json.decodeFromJsonElement<T>(jsonElement)
    } catch (_: Exception) {
        null
    }
}

