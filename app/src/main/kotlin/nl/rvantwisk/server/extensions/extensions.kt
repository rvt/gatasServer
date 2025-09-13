package nl.rvantwisk.server.extensions

import kotlinx.serialization.ExperimentalSerializationApi
import nl.rvantwisk.gatas.extensions.combineBuffers
import nl.rvantwisk.gatas.extensions.serializeAircraftPositionV1
import nl.rvantwisk.gatas.models.AircraftPosition
import kotlin.math.abs

fun Double.equalsWithTolerance(other: Double, epsilon: Double = 1e-10): Boolean {
    return abs(this - other) < epsilon
}


/*
* Encode a List<AircraftPosition> to cobs dataset
* Application -> GATAS
*/
@OptIn(ExperimentalSerializationApi::class)
fun List<AircraftPosition>.serializeAircraftPositionsV1(): ByteArray = this.map {
  it.serializeAircraftPositionV1()
}.combineBuffers(0)

fun Long.toIPv4(): String {
  val b4 = (this shr 24 and 0xFFL).toInt()
  val b3 = (this shr 16 and 0xFFL).toInt()
  val b2 = (this shr 8 and 0xFFL).toInt()
  val b1 = (this and 0xFFL).toInt()
  return "$b1.$b2.$b3.$b4"
}
