package nl.rvantwisk.gatas.server.extensions

import kotlinx.serialization.ExperimentalSerializationApi
import nl.rvantwisk.gatas.lib.extensions.combineBuffers
import nl.rvantwisk.gatas.lib.extensions.serializeAircraftPositionV1
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import kotlin.math.abs


/**
 * Compare with a other Double to see if they are equal within a tolerance
 */
fun Double.fuzzyEquals(other: Double, epsilon: Double = 1e-6): Boolean {
    return abs(this - other) < epsilon
}


/**
 * Encode a List<AircraftPosition> to cobs dataset
 * Usually used to encode Server data to GA/TAS
 */
@OptIn(ExperimentalSerializationApi::class)
fun List<AircraftPosition>.serializeAircraftPositionsV1(): ByteArray = this.map {
    it.serializeAircraftPositionV1()
}.combineBuffers(0)

/**
 *  Transform a Long (4Byte) from GA/TAS and convert it to an IPv4 address String
 */
fun Long.toIPv4(): String {
    val b4 = (this shr 24 and 0xFFL).toInt()
    val b3 = (this shr 16 and 0xFFL).toInt()
    val b2 = (this shr 8 and 0xFFL).toInt()
    val b1 = (this and 0xFFL).toInt()
    return "$b1.$b2.$b3.$b4"
}
