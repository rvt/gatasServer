package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject


val json = Json { ignoreUnknownKeys = true }

/**
 * Transform a Tile38NearbyResult to a List of objects on what it contains
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38NearbyResult.fromJsonField(): List<T> {
    val jsonIdx = this.fields.indexOf("json")
    if (jsonIdx == -1) return emptyList()

    return this.objects.map {
        json.decodeFromJsonElement<T>(it.jsonObject["fields"]!!.jsonArray[jsonIdx])
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38NearbyResult.listOfMap(): List<Map<String, String>> {
    return this.objects.map { obj ->
        val values = obj.jsonObject["fields"]!!.jsonArray
        this.fields.mapIndexed { idx, field ->
            field to values[idx].toString()
        }.toMap()
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38NearbyResult.fromJsonField(name: String): List<T> {
    val idx = this.fields.indexOf(name)
    if (idx == -1) return emptyList()

    return this.objects.map { obj ->
        val raw = obj.jsonObject["fields"]!!.jsonArray[idx].toString()
        when (T::class) {
            Double::class -> raw.toDouble() as T
            Int::class -> raw.toInt() as T
            String::class -> raw as T
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Tile38NearbyResult.listOf_w(name: String, json: Json = Json): List<T> {
    val idx = fields.indexOf(name)
    if (idx == -1) return emptyList()

    return objects.map { obj ->
        val element = obj.jsonObject["fields"]!!.jsonArray[idx]
        json.decodeFromJsonElement<T>(element)
    }
}

