package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@ExperimentalSerializationApi
data class Tile38NearbyResult(
    val ok: Boolean,
    val objects: List<JsonElement> = emptyList(),
    val fields: List<String> = emptyList(),
    val count: Int,
    val cursor: Int,
    val elapsed: String
)
