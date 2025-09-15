package nl.rvantwisk.server.udp

import co.touchlab.kermit.Logger
import io.github.kotlin.fibonacci.nl.rvantwisk.gatas.models.SetIcaoAddressV1
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import nl.rvantwisk.gatas.extensions.AIRCRAFT_CONFIGURATIONS_V1
import nl.rvantwisk.gatas.extensions.AIRCRAFT_POSITION_REQUEST_V1
import nl.rvantwisk.gatas.extensions.CobsByteArray
import nl.rvantwisk.gatas.extensions.deserializeAircraftConfigurationV1
import nl.rvantwisk.gatas.extensions.deserializeOwnshipPositionV1
import nl.rvantwisk.gatas.extensions.serializeSetIcaoAddressV1
import nl.rvantwisk.gatas.models.updateEstimGeomAltitude
import nl.rvantwisk.server.datastore.MetarService
import nl.rvantwisk.server.datastore.SpatialService
import nl.rvantwisk.server.extensions.serializeAircraftPositionsV1
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named


class UdpAircraftService : KoinComponent {
  private val rateLimiter: SimpleRateLimiter by inject(named("SimpleRateLimiter"))

  private val log: Logger by inject { parametersOf(UdpAircraftService::class.simpleName!!) }

  private val tile38: SpatialService by inject(named("SpatialService"))
  private val metarService: MetarService by inject(named("MetarService"))
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  @OptIn(
    ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class,
    ExperimentalStdlibApi::class
  )
  fun start() {
    log.i { "UdpAircraftService started" }
    scope.launch {
      runUdpServer(port = 16256, scope = scope)
    }
  }


  private suspend fun runUdpServer(
    port: Int,
    scope: CoroutineScope,
  ) {
    val selectorManager = SelectorManager(Dispatchers.IO)

    val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", port))
    println("\uD83D\uDD0C UDP server listening on port $port")

    while (true) {
      val datagram = socket.receive()
      val received = datagram.packet.readByteArray()
      val sender = datagram.address

      // Run packet handler in outer scope to keep listener non-blocking
      scope.launch {
        try {
//          log.i { "Received ${received.size} bytes from $sender" }
          val reply = udpRequestHandler(received, sender)
          if (reply != null && reply.isNotEmpty()) {
            socket.send(Datagram(buildPacket { writeFully(reply) }, sender))
          }
        } catch (e: Exception) {
          println("Error processing UDP packet: ${e.message}")
        }
      }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private suspend fun udpRequestHandler(data: ByteArray, sender: SocketAddress): ByteArray? {

    if (!rateLimiter.tryConsume(sender as InetSocketAddress)) {
      log.w { "Client rate limited ${sender}" }
      return null
    }

    var returnedData = ByteArray(0)
    var lat = 0.0
    var lon = 0.0

    // Split by 0x00 to handle multiple COBS-encoded messages
    val messages = data.split(0.toByte())

    for (msg in messages) {
      if (msg.isEmpty()) continue // skip empty chunks
      val cobsByteArray = CobsByteArray(msg)

      if (cobsByteArray.peekAhead() == AIRCRAFT_POSITION_REQUEST_V1) {
        runCatching {
          val metarCache = metarService.getCache()
          val ownship = deserializeOwnshipPositionV1(cobsByteArray)
          lat = ownship.latitude
          lon = ownship.longitude

          tile38.updateOwnship(ownship)

          val aircrafts = tile38.getAircraft(
            ownship.latitude,
            ownship.longitude,
            ownship.ellipsoidHeight
          ).map {
            it.updateEstimGeomAltitude(metarCache.getMetar(it.latitude, it.longitude))
            it
          }

          returnedData += aircrafts.serializeAircraftPositionsV1()
        }
        continue
      }

      if (cobsByteArray.peekAhead() == AIRCRAFT_CONFIGURATIONS_V1) {
        runCatching {
          val dataMsg = deserializeAircraftConfigurationV1(cobsByteArray)

          tile38.sendAircraftConfig(lat, lon, dataMsg)
          val inDb = tile38.getFieldsMap("fleetConfig", dataMsg.gatasId, listOf("newIcaoAddress"))

          val intValue: Int = inDb["newIcaoAddress"] as Int
          val uintValue = intValue.toUInt()
          if (dataMsg.icaoAddress != uintValue && uintValue in dataMsg.icaoAddressList) {
            log.i { "Trying to configure GA/TAS" }
            returnedData += SetIcaoAddressV1(intValue.toUInt()).serializeSetIcaoAddressV1()
          }
        }
        continue
      }
    }

    return if (returnedData.isNotEmpty()) returnedData else null
  }

  // Helper: split ByteArray by delimiter
  private fun ByteArray.split(delimiter: Byte): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    var start = 0
    for (i in indices) {
      if (this[i] == delimiter) {
        if (i > start) {
          result += copyOfRange(start, i)
        }
        start = i + 1
      }
    }
    if (start < size) {
      result += copyOfRange(start, size)
    }
    return result
  }

}
