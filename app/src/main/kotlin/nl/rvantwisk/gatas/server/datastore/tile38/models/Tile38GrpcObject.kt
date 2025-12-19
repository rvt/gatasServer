package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@ExperimentalSerializationApi
data class Tile38GrpcObject(
    val hook: String,
    val key: String,
    val time: String,
    val id: String,
    @SerialName("object")
    val coordinate: Coordinate,
    val fields: Fields
)
