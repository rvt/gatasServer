package nl.rvantwisk.gatas.server.datastore.tile38.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.rvantwisk.gatas.server.metar.model.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class MetarH3 @OptIn(ExperimentalTime::class) constructor(
    @XmlElement(true)
    // Airport id
    val id: String,
    @SerialName("elev")
    val elevation: Long, // Elevation of the runway in meters
    @SerialName("lat")
    val latitude: Double,
    @SerialName("lon")
    val longitude: Double,
    val qnh: Double,
    @Serializable(with = InstantSerializer::class)
    @SerialName("otime")
    val observationTime: Instant,
)
