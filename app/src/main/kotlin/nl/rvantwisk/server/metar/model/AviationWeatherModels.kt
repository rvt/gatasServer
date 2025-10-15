package nl.rvantwisk.server.metar.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import nl.adaptivity.xmlutil.serialization.XmlElement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object InstantSerializer : KSerializer<Instant> {
  override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

  @OptIn(ExperimentalTime::class)
  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(value.toString())
  }

  @OptIn(ExperimentalTime::class)
  override fun deserialize(decoder: Decoder): Instant {
    return Instant.parse(decoder.decodeString())
  }
}

@Serializable
@SerialName("response")
data class Response(
  @XmlElement(true)
  val request_index: Long,

  @XmlElement(true)
  val data_source: DataSource,

  @XmlElement(true)
  val data: Data
)

@Serializable
@SerialName("data")
data class Data(
  @XmlElement(true)
  val METAR: List<Metar>
)

@Serializable
@SerialName("data_source")
data class DataSource(
  @XmlElement(false)
  val name: String
)

@Serializable
@SerialName("METAR")
data class Metar @OptIn(ExperimentalTime::class) constructor(
  @XmlElement(true)
  val raw_text: String,

  @XmlElement(true)
  val station_id: String,

  @XmlElement(true)
  @Serializable(with = InstantSerializer::class)
  val observation_time: Instant,

  @XmlElement(true)
  val latitude: Double?,

  @XmlElement(true)
  val longitude: Double?,

//    @XmlElement(true)
//    val temp_c: Double,
//
//    @XmlElement(true)
//    val dewpoint_c: Double,
//
//    @XmlElement(true)
//    val wind_dir_degrees: Double,
//
//    @XmlElement(true)
//    val wind_speed_kt: Double,
//
//    @XmlElement(true)
//    val precip_in: Double,
//
//    @XmlElement(true)
//    val visibility_statute_mi: String,

  @XmlElement(true)
  val altim_in_hg: Double?,

  @XmlElement(true)
  val sea_level_pressure_mb: Double?,

//    @XmlElement(true)
//    val wind_gust_kt: Double?,
//
//    @XmlElement(true)
//    val quality_control_flags: QualityControlFlags?,
//
//    @XmlElement(true)
//    val sky_condition: List<SkyCondition> = listOf(),
//
//    @XmlElement(true)
//    val flight_category: String,
//
//    @XmlElement(true)
//    val metar_type: String,
//
//    @XmlElement(true)
//    val wx_string: String?,

  @XmlElement(true)
  // When setting to 0, we will still get an metar, but perhaps their height above sea level is not available
  val elevation_m: Long = 0
) {
  fun qnh() = sea_level_pressure_mb ?: (altim_in_hg!! * 33.86389)
}

@Serializable
data class MetarH3 @OptIn(ExperimentalTime::class) constructor(
  @XmlElement(true)
  val id: String,

  val latitude: Double,
  val longitude: Double,

  val qnh: Double,

  val elevation_m: Long,

  @Serializable(with = InstantSerializer::class)
  val observation_time: Instant,
)

@OptIn(ExperimentalTime::class)
fun Metar.toH3(): MetarH3 {
  val qnhValue = qnh()

  return MetarH3(
    id = station_id,
    latitude = latitude!!,
    longitude = longitude!!,
    qnh = qnhValue,
    elevation_m = elevation_m,
    observation_time = observation_time
  )
}

@Serializable
@SerialName("quality_control_flags")
data class QualityControlFlags(
  @XmlElement(true)
  val no_signal: Boolean?,
  @XmlElement(true)
  val auto: Boolean?,
  @XmlElement(true)
  val auto_station: Boolean?,
  @XmlElement(true)
  val lightning_sensor_off: Boolean?,
)

@Serializable
@SerialName("sky_condition")
data class SkyCondition(
  @XmlElement(false)
  val sky_cover: String,

  @XmlElement(false)
  val cloud_base_ft_agl: Int
)
