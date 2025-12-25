package nl.rvantwisk.gatas.lib.services;

import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.services.egm2008.EarthGravitationalModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.pow

class AltitudeService : KoinComponent {

    val egm: EarthGravitationalModel by inject<EarthGravitationalModel>()

    /**
     * After this call ellipsoidHeight is set to an appropriate height.
     * if ellipsoidHeight was known, we leave it as is. When it was null, it's based on local QNH
     * and barometric altitude.
     * When no baro was knoiwn, and no ellipsoid Height, and it wa snot on ground, ellipsoid is set to -9999
     */
    fun updateEstimGeomAltitude(pos: AircraftPosition, localQnh: Double) {
        // ADS-B geometric altitude wins
        if (pos.ellipsoidHeight != null) {
            return
        }
        if (pos.baroAltitude == null ) {
            if (pos.isGround) {
                // Get a DEM file or somehow get this from Tile38 when an aircraft is on Ground
                pos.ellipsoidHeight = egm.egmGeoidOffset(pos.latitude, pos.longitude)
            } else {
                // Set to -9999 so to keep it in Tle38 for analysis
                pos.ellipsoidHeight = -9999
            }
            return
        }

        // We take the QNH of what we know based on location. We sometimes see that the QNH of the aircraft is set to STD, even though
        // they are already flying below transition altitude
        val ownQnh = localQnh.toInt() // pos.qnh ?: localQnh

        val mslAltitude = getCorrectedAltitude(pos.baroAltitude, ownQnh.toDouble())
        val geoidOffset = egm.egmGeoidOffset(pos.latitude, pos.longitude)
        val ellipsoid = mslAltitude + geoidOffset;
//        println("calculated: $ellipsoid given: ${pos.ellipsoidHeight} baro:${pos.baroAltitude} ownQnh:$ownQnh localQnh: $localQnh")

        pos.ellipsoidHeight = (ellipsoid).toInt()
    }

    fun getCorrectedAltitude(baroAltMeters: Int, localQnh: Double): Double {
        val STANDARD_PRESSURE = 1013.25
        val ISA_EXPONENT = 5.25588 // (g * M) / (R * L)
        val TEMPERATURE_LAPSE_RATE = 0.0065 // K/m
        val STANDARD_TEMP_K = 288.15 // 15°C in Kelvin

        val stationPressure =
            STANDARD_PRESSURE * (1.0 - (TEMPERATURE_LAPSE_RATE * baroAltMeters) / STANDARD_TEMP_K).pow(ISA_EXPONENT)
        val correctedAlt =
            (STANDARD_TEMP_K / TEMPERATURE_LAPSE_RATE) * (1.0 - (stationPressure / localQnh).pow(1.0 / ISA_EXPONENT))

        return correctedAlt
    }
}
