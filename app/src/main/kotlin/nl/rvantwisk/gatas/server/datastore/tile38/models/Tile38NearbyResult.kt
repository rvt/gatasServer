package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
@ExperimentalSerializationApi
data class Tile38NearbyResult(
    val ok: Boolean,
    val objects: List<JsonObject> = emptyList(),
    val fields: List<String> = emptyList(),
    val elapsed: String,
    val count: Int? = null,
    val cursor: Int? = null,
    val err: String? = null
)
@Serializable
@ExperimentalSerializationApi
data class Tile38GetResult(
    val ok: Boolean,
    @SerialName("object")
    val obj: JsonElement? = null,
    val fields: JsonObject? = null,
    val elapsed: String,
    val err: String? = null
)
