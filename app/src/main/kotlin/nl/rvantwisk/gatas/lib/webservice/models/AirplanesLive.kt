package nl.rvantwisk.gatas.lib.webservice.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirplanesLiveResponseDto(
    val ac: List<AirplanesLiveAircraftDto> = emptyList()
)

@Serializable
data class AirplanesLiveRestResponseDto(
    @SerialName("aircraft")
    val ac: List<AirplanesLiveAircraftDto> = emptyList()
)

@Serializable
data class AirplanesLiveAircraftDto(
    val hex: String,
    val type: String,
    val flight: String? = null,
    val r: String? = null,
    val t: String? = null,
    val desc: String? = null,
    @SerialName("alt_baro") val altBaro: String? = null,
    @SerialName("alt_geom") val altGeom: Int? = null,
    val gs: Double? = null,
    val ias: Int? = null,
    val tas: Int? = null,
    val mach: Double? = null,
    val oat: Int? = null,
    val tat: Int? = null,
    val track: Double? = null,
    val dir: Double? = null,
    @SerialName("track_rate") val trackRate: Double? = null,
    val roll: Double? = null,
    @SerialName("mag_heading") val magHeading: Double? = null,
    @SerialName("true_heading") val trueHeading: Double? = null,
    @SerialName("baro_rate") val baroRate: Double? = null,
    @SerialName("geom_rate") val geomRate: Double? = null,
    val squawk: String? = null,
    val emergency: String? = null,
    val category: String? = null,
    @SerialName("nav_qnh") val navQnh: Double? = null,
    @SerialName("nav_altitude_mcp") val navAltitudeMcp: Int? = null,
    @SerialName("nav_heading") val navHeading: Double? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    val version: Int? = null,
    @SerialName("nic_baro") val nicBaro: Int? = null,
    @SerialName("nac_p") val nacP: Int? = null,
    @SerialName("nac_v") val nacV: Int? = null,
    val sil: Int? = null,
    @SerialName("sil_type") val silType: String? = null,
    val gva: Int? = null,
    val sda: Int? = null,
    val alert: Int? = null,
    val spi: Int? = null,
    val nav_modes: List<String> = emptyList(),
    val mlat: List<String> = emptyList(),
    val tisb: List<String> = emptyList(),
    val messages: Long? = null,
    val seen: Double? = null,
    val dist: Double? = null,
    val rssi: Double? = null,
    val seen_pos: Double? = null

)
