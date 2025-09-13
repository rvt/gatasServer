package nl.rvantwisk.gatas.models

import kotlinx.serialization.Serializable
import nl.rvantwisk.gatas.extensions.METERS_TO_FT
import nl.rvantwisk.gatas.extensions.footToMeter
import nl.rvantwisk.gatas.services.egm2008.Egm2008Reader
import kotlin.math.pow


@Serializable
data class AircraftPosition(
//    val ownshipLatitude: Double,
//    val ownshipLongitude: Double,
//    val ownshipEllipsoidHeight: Int,  // in meters

    val id: UInt,
    val dataSource: DataSource,
    val addressType: AddressType,
    val latitude: Double,
    val longitude: Double,
    var ellipsoidHeight: Int,            // In Meter to MSL estimated based on baro when no geo altitude is send by the aircraft
    val course: Double,             // Degree
    val hTurnRate: Double,          // Degree per second
    val groundSpeed: Double,       // In meters per second
    val verticalSpeed: Double,      // In meters per second
    val aircraftCategory: AircraftCategory,

    val callSign: String,
    val qnh: Double?,


    val nicBaro: Int,              // When 1 cross checked and correct
    val _ellipsoidHeight: Int?,    // In meters
    val _baroAltitude: String?,    // In meters
)

/**
 * Update heightEllipse from heightEllipse when heightEllipse is known.
 * When heightEllipse was not, estimate altitude from local QNH and baro altitude
 * The end result will always be an altitude in meters to the WGS84 ellipse
 *
 * NOTE: When planes fly in transition altitude we do not have a way of knowing this and as such calculations
 * are not accurate. We also do not know the transition altitude because that differs by region.
 * That means that Altitude of any aircraft by only Baro metric pressure is not accurate
 */
fun AircraftPosition.updateEstimGeomAltitude(localQnh: Double) {
    // Ellipsoid height given, this is what we also want and is send by ADS-B
    if (_ellipsoidHeight != null) {
        ellipsoidHeight = _ellipsoidHeight
        return
    }

    val ownQnh = qnh ?: localQnh

    val baroAlt = if (_baroAltitude == null || _baroAltitude.lowercase() == "ground") {
        // TODO: We need to have a DEM file to properly set it to the ground
        0.0
    } else {
        _baroAltitude.toDoubleOrNull()?.footToMeter() ?: 0.0
    }

    val geoIdOffset = Egm2008Reader.egmGeoidOffset(latitude, longitude)
    ellipsoidHeight = adjustBaroAlt(
        baroAlt + geoIdOffset.toDouble(),
        ownQnh,
    ).toInt() + Egm2008Reader.egmGeoidOffset(latitude, longitude)
}

// Based on: https://www.weather.gov/media/epz/wxcalc/pressureAltitude.pdf
// ISA atmosphere model
fun adjustBaroAlt(baroAltMeters: Double, localQnh: Double): Double {
    val stationPressure = (1 - (baroAltMeters * METERS_TO_FT) / 145366.45).pow(5.2553026) * 1013.25
    var correctedAlt = (1 - (stationPressure / localQnh).pow(0.190284)) * 145366.45

    return correctedAlt.footToMeter()
}
