package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.Serializable

@Serializable
data class Coordinate(
    val type: String?,
    // In the form of Long/Lat it's reversed!! example: 52.5, 5.2 for somewhere in NL
    val coordinates: ArrayList<Double>
)

