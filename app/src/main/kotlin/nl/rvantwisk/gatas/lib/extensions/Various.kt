package nl.rvantwisk.gatas.lib.extensions

import nl.rvantwisk.gatas.lib.math.CRC16

/**
 * Converts meters to nautical miles.
 * @param meters The distance in meters.
 * @return The distance in nautical miles.
 */
fun Int.meterToNauticalMiles(): Double {
    return (this / NM_TO_METERS) / 1000.0
}

fun Double.meterToNauticalMiles(): Double {
    return (this / NM_TO_METERS) / 1000.0
}

//fun Int.footToMeter(): Double {
//    return this * FT_TO_METERS
//}

fun Int.footToMeter(): Int {
    return (this * FT_TO_METERS).toInt()
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
