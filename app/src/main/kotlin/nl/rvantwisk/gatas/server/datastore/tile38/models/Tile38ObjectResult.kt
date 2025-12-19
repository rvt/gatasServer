package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@ExperimentalSerializationApi
data class Tile38ObjectResult(
    val ok: Boolean,
    val elapsed: String,
//  @kotlinx.serialization.Transient
    val err: String? = null,
//  @kotlinx.serialization.Transient
    val `object`: JsonElement? = null,
//  @kotlinx.serialization.Transient
    val fields: JsonElement? = null,
)
