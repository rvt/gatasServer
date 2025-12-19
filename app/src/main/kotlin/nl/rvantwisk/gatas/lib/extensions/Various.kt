package nl.rvantwisk.gatas.lib.extensions

import nl.rvantwisk.gatas.lib.math.CRC16
import nl.rvantwisk.gatas.lib.models.AircraftCategory

/**
 * Converts meters to nautical miles.
 * @param meters The distance in meters.
 * @return The distance in nautical miles.
 */
fun Int.meterToNauticalMiles(): Double {
    return this / 1852.0
}
fun Double.meterToNauticalMiles(): Double {
    return this / 1852.0
}
fun Int.footToMeter(): Double {
    return this * FT_TO_METERS
}

fun Double.footToMeter(): Double {
    return this * FT_TO_METERS
}

fun ByteArray.setCRC16(): ByteArray {
    require(size >= 2) { "ByteArray must have at least 2 bytes reserved for CRC" }

    val crc = CRC16.crc16(this, 0, size - 2)
    this[this.size - 2] = (crc shr 8).toByte()
    this[this.size - 1] = crc.toByte()
    return this
}

fun List<ByteArray>.combineBuffers( extraBytes: Int): ByteArray {
    val dataSize = this.sumOf { it.size }
    val combined = ByteArray(dataSize + extraBytes)
    var offset = 0
    for (part in this) {
        part.copyInto(combined, destinationOffset = offset)
        offset += part.size
    }
    return combined
}

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToUint():UInt = this.hexToLong().toUInt()

fun AircraftCategory.toFlarm(): Char = when (this) {
    AircraftCategory.GLIDER -> '1'
    // Assume a TOW_PLANE is not defined, so skip '2'
    AircraftCategory.ROTORCRAFT,
    AircraftCategory.GYROCOPTER -> '3'
    AircraftCategory.SKY_DIVER -> '4'
    AircraftCategory.DROP_PLANE -> '5'
    AircraftCategory.HANG_GLIDER -> '6'
    AircraftCategory.PARA_GLIDER -> '7'
    AircraftCategory.LIGHT,
    AircraftCategory.SMALL,
    AircraftCategory.ULTRA_LIGHT_FIXED_WING,
    AircraftCategory.AEROBATIC -> '8'
    AircraftCategory.LARGE,
    AircraftCategory.HIGH_VORTEX,
    AircraftCategory.HEAVY_ICAO -> '9'
    AircraftCategory.UNKNOWN -> 'A'
    AircraftCategory.LIGHT_THAN_AIR -> 'B'
    AircraftCategory.UN_MANNED -> 'D'
    AircraftCategory.POINT_OBSTACLE,
    AircraftCategory.LINE_OBSTACLE,
    AircraftCategory.CLUSTER_OBSTACLE -> 'F'
    AircraftCategory.SURFACE_VEHICLE,
    AircraftCategory.SURFACE_EMERGENCY_VEHICLE -> 'F'
    else -> 'A'
}
