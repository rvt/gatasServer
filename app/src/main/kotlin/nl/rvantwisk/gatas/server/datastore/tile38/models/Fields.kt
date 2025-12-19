package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.Serializable

@Serializable
data class Fields(
    val speed: Double,
    val track: Double,
    val turn: Double,
    val vrate: Double
)
