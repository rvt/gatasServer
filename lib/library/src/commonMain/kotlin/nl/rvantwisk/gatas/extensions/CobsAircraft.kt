package nl.rvantwisk.gatas.extensions

import io.github.kotlin.fibonacci.nl.rvantwisk.gatas.models.OwnshipAircraftConfiguration
import io.github.kotlin.fibonacci.nl.rvantwisk.gatas.models.SetIcaoAddressV1
import nl.rvantwisk.gatas.models.AddressType
import nl.rvantwisk.gatas.models.AircraftCategory
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.OwnshipPosition
import kotlin.math.roundToInt

/**
 * Encode a AircraftPosition to a COBS array.
 * Application -> GATAS
 */
fun AircraftPosition.serializeAircraftPositionV1(): ByteArray {
  val RAW_ARRAY_SIZE = 24
  val callSignBytes = callSign.take(MAX_CALLSIGN_LENGTH).encodeToByteArray()

  val cobsBuffer = CobsByteArray(RAW_ARRAY_SIZE + callSignBytes.size)

  // @formatter:off
  cobsBuffer.put1(AIRCRAFT_POSITION_TYPE_V1)
  cobsBuffer.putUInt3(id)
  cobsBuffer.put1(addressType.value.toByte())
  cobsBuffer.put1(dataSource.value.toByte())
  cobsBuffer.putInt4((latitude * 1E7).roundToInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE))
  cobsBuffer.putInt4((longitude * 1E7).roundToInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE))
  cobsBuffer.put2(((ellipsoidHeight.coerceIn(-100, 65535-100)) + 100).toShort())
  cobsBuffer.put1((course / (360.0 / 255.0)).toInt().coerceIn(0, 255).toByte())
  cobsBuffer.put1((hTurnRate.coerceIn(-25.0, 25.0) * 5).roundToInt().toByte())
  cobsBuffer.put2((groundSpeed.coerceIn(0.0, 655.0) * 100).roundToInt().toShort())
  cobsBuffer.put2((verticalSpeed.coerceIn(-32.0, 32.0) * 1024).roundToInt().toShort())
  cobsBuffer.put1(aircraftCategory.value.toByte())
  cobsBuffer.putArray(callSignBytes)
  // @formatter:off

  return cobsBuffer.getCobs()
}

/**
 * Encode ownship position to a COBS array.
 * Application -> GATAS
 */
//fun OwnshipPosition.toCobs(): ByteArray {
//    val  COBS_ARRAY_SIZE = 21
//    val cobsBuffer = CobsByteArray(COBS_ARRAY_SIZE)
//
//    // @formatter:off
//    cobsBuffer.put1(COBS_OWNERSHIP_POSITION_TYPE)
//    cobsBuffer.putInt3(id)
//    cobsBuffer.putInt4((latitude * 1E7).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
//    cobsBuffer.putInt4((longitude * 1E7).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
//    cobsBuffer.put2((ellipsoidHeight + 100).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
//    cobsBuffer.put1((track / (360.0 / 255.0)).toInt().coerceIn(0, 255).toByte())
//    cobsBuffer.put1((turnRate * 5).roundToInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte())
//    cobsBuffer.put2((groundSpeed * 10).roundToInt().coerceIn(0, 65535).toShort())
//    cobsBuffer.put2((verticalRate * 100).roundToInt().coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt()).toShort())
//    cobsBuffer.put1(adslCategory.coerceIn(0, 255).toByte())
//    // @formatter:on
//
//    return cobsBuffer.getCobs()
//}

/**
 * Decode ownship position from a COBS array.
 * GATAS -> Application
 */
fun deserializeOwnshipPositionV1(cobs: CobsByteArray): OwnshipPosition {

  val type = cobs.getInt1()
  require(type == AIRCRAFT_POSITION_REQUEST_V1) { "Invalid type byte: $type" }

  val epoch = cobs.getUInt4()
  val address = cobs.getInt3()
  val addressType = cobs.get1().toUByte()
  val category = cobs.get1().toUByte()
  val lat = cobs.getInt4().toDouble() / 1E7
  val lon = cobs.getInt4().toDouble() / 1E7
  val heightEllipse = cobs.get2().toInt() - 100

  val track = cobs.get1().toUByte().toInt() * (360.0 / 255.0)
  val turnRate = cobs.get1().toDouble() / 5.0
  val groundSpeed = cobs.get2().toUShort().toInt() / 10.0
  val verticalRate = cobs.get2().toDouble() / 100.0

  return OwnshipPosition(
    epoch = epoch,
    id = address,
    latitude = lat,
    longitude = lon,
    ellipsoidHeight = heightEllipse,
    turnRate = turnRate,
    track = track,
    groundSpeed = groundSpeed,
    verticalRate = verticalRate,
    category = AircraftCategory.fromUByte(category),
    addressType = AddressType.fromUByte(addressType)
  )
}

fun deserializeAircraftConfigurationV1(cobs: CobsByteArray): OwnshipAircraftConfiguration {

  val type = cobs.getInt1()
  // Example: 03.de.e8.ba.1f.00.00.00.00.ff.ff.ff.01
  require(type == AIRCRAFT_CONFIGURATIONS_V1) { "Invalid type byte: $type" }

  val gatasId = cobs.getUInt4()
  val gatasIp = cobs.getUInt4()
  val icaoAddress = cobs.getUInt3()
  val options = cobs.getUInt1()
  val numberOfAddresses = cobs.getInt1()
  val icaoAddressList = List(numberOfAddresses) { cobs.getUInt3() }

  return OwnshipAircraftConfiguration(
    gatasId = gatasId,
    icaoAddress = icaoAddress,
    options = options,
    newIcaoAddress = null,
    icaoAddressList = icaoAddressList,
    gatasIp = gatasIp
  )
}

fun SetIcaoAddressV1.serializeSetIcaoAddressV1(): ByteArray {
  val cobsBuffer = CobsByteArray(4)
  cobsBuffer.put1(SET_ICAO_ADDRESS_V1)
  cobsBuffer.putUInt3(this.icaoAddress)
  return cobsBuffer.getCobs()
}
