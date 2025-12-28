package nl.rvantwisk.gatas.server.udp

import co.touchlab.kermit.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import nl.rvantwisk.gatas.lib.extensions.*
import nl.rvantwisk.gatas.lib.math.distanceFast
import nl.rvantwisk.gatas.lib.models.SetIcaoAddressV1
import nl.rvantwisk.gatas.lib.services.AltitudeService
import nl.rvantwisk.gatas.server.REQUEST_GROUND_DIST
import nl.rvantwisk.gatas.server.REQUEST_MAX_AIRCRAFT
import nl.rvantwisk.gatas.server.REQUEST_MAX_DIST
import nl.rvantwisk.gatas.server.datastore.MetarCacheFactoryService
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.extensions.serializeAircraftPositionsV1
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource


class UdpAircraftService : KoinComponent {
    private val rateLimiter: SimpleRateLimiter by inject(named("SimpleRateLimiter"))
    private val log: Logger by inject { parametersOf(UdpAircraftService::class.simpleName!!) }

    private val tile38: SpatialService by inject(named("SpatialService"))
    private val altitudeService: AltitudeService by inject()
    private val metarCacheFactoryService: MetarCacheFactoryService by inject(named("MetarService"))
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(
        ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class, ExperimentalStdlibApi::class
    )
    fun start() {
        log.i { "UdpAircraftService started" }
        scope.launch {
            runUdpServer(port = 3000, scope = scope)
        }
    }


    private suspend fun runUdpServer(
        port: Int,
        scope: CoroutineScope,
    ) {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", port))
        log.i { "UDP server listening on port $port" }

        try {
            coroutineScope {
                // This loop automatically stops when the coroutine is cancelled
                while (isActive) {
                    try {
                        val datagram = socket.receive()
                        val received = datagram.packet.readByteArray()
                        val sender = datagram.address

                        // Launch handler in parent scope so it can be cancelled with the server
                        scope.launch {
                            try {
                                // Handle incoming request
                                val reply = withTimeout(900) {
                                    // log.i { "Received ${received.size} bytes from $sender" }
                                    udpRequestHandler(received, sender)
                                }

                                // If the replay is empty, at least send a fallback
                                val actualReply =
                                    if (reply.isNotEmpty()) reply else byteArrayOf(0xfe.toByte(), 0x01, 0x01)

                                socket.send(Datagram(buildPacket { writeFully(actualReply) }, sender))


                            } catch (e: TimeoutCancellationException) {
                                log.w { "Handler took too long (${e.message}), sending fallback" }
                                socket.send(
                                    Datagram(
                                        buildPacket { writeFully(byteArrayOf(0xff.toByte(), 0x01, 0x01)) }, sender
                                    )
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e // propagate cancellation
                                log.e(e) { "Error ${e.message}" }
                            }
                        }
                    } catch (e: CancellationException) {
                        log.i { "UDP server cancelled" }
                        throw e // stop loop cleanly
                    } catch (e: Exception) {
                        log.e(e) { "Socket error: ${e.message}" }
                        delay(200) // small backoff to prevent tight failure loops
                    }
                }
            }
        } finally {
            log.i { "Shutting down UDP server on port $port" }
            try {
                socket.close()
            } catch (_: Exception) {
            }
            try {
                selectorManager.close()
            } catch (_: Exception) {
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun udpRequestHandler(data: ByteArray, sender: SocketAddress): ByteArray {
        val timeSource = TimeSource.Monotonic
        val markStart = timeSource.markNow()

        var returnedData = ByteArray(0)
        if (!rateLimiter.tryConsume(sender as InetSocketAddress)) {
            log.w { "Client rate limited ${sender}" }
            return returnedData
        }

        var lat = 0.0
        var lon = 0.0

        // Split by 0x00 to handle multiple COBS-encoded messages
        val messages = data.split(0.toByte())
        var numAircraft = -1
        for (msg in messages) {
            if (msg.size < 2) {
                log.i { "No Data" }
                continue
            }

            val cobsByteArray = CobsByteArray(msg)

            if (cobsByteArray.peekAhead() == AIRCRAFT_POSITION_REQUEST_V1) {
                // log.i { "Found AIRCRAFT_POSITION_REQUEST_V1" }
                runCatching {
                    val metarCache = metarCacheFactoryService.cacheFactory()
                    val ownship = deserializeOwnshipPositionV1(cobsByteArray)
                    lat = ownship.latitude
                    lon = ownship.longitude

                    tile38.updateOwnship(ownship)

                    val aircrafts = tile38.getAircraft(
                        ownship.latitude, ownship.longitude, ownship.ellipsoidHeight
                    ).map {
                        altitudeService.updateEstimGeomAltitude(it, metarCache.getQNH(it.latitude, it.longitude))
                        it
                    }.filterByDistanceOnGround(
                            ownship.latitude, ownship.longitude, REQUEST_GROUND_DIST, REQUEST_MAX_DIST
                        ).sortedBy { position ->
                            val rel = distanceFast(
                                ownship.latitude, ownship.longitude, position.latitude, position.longitude
                            )
                            rel
                        }.take(REQUEST_MAX_AIRCRAFT)
                    if (aircrafts.isEmpty()) {
                        log.i { "No aircrafts found around ownship ${ownship.latitude} ${ownship.longitude}" }
                    }
                    numAircraft = aircrafts.size
                    returnedData += aircrafts.serializeAircraftPositionsV1()
                }.onFailure {
                    log.w { "Error parsing AIRCRAFT_POSITION_REQUEST_V1 ${it.message}" }
                }
                continue
            }

            if (cobsByteArray.peekAhead() == AIRCRAFT_CONFIGURATIONS_V1) {
                // log.i { "Found AIRCRAFT_CONFIGURATIONS_V1" }
                runCatching {
                    val dataMsg = deserializeAircraftConfigurationV1(cobsByteArray)

                    tile38.setFleetConfig(lat, lon, dataMsg)
                    val inDb = tile38.getFieldsMap("fleetConfig", dataMsg.gatasId, listOf("newIcaoAddress"))

                    val intValue: Int = (inDb["newIcaoAddress"] as? Int) ?: 0
                    val uintValue = intValue.toUInt()
                    if (dataMsg.icaoAddress != uintValue && uintValue in dataMsg.icaoAddressList) {
                        log.i { "Trying to configure GA/TAS" }
                        returnedData += SetIcaoAddressV1(intValue.toUInt()).serializeSetIcaoAddressV1()
                    }
                }.onFailure {
                    log.w { "Error parsing AIRCRAFT_CONFIGURATIONS_V1 ${it.message}" }
                }
                continue
            }

            if (cobsByteArray.peekAhead() == AIRCRAFT_CONFIGURATIONS_V2) {
                // log.i { "Found AIRCRAFT_CONFIGURATIONS_V2" }
                runCatching {
                    val dataMsg = deserializeAircraftConfigurationV2(cobsByteArray)

                    tile38.setFleetConfig(lat, lon, dataMsg)
                    val inDb = tile38.getFieldsMap("fleetConfig", dataMsg.gatasId, listOf("newIcaoAddress"))

                    val intValue: Int = (inDb["newIcaoAddress"] as? Int) ?: 0
                    val uintValue = intValue.toUInt()
                    if (dataMsg.icaoAddress != uintValue && uintValue in dataMsg.icaoAddressList) {
                        log.i { "Trying to configure GA/TAS" }
                        returnedData += SetIcaoAddressV1(intValue.toUInt()).serializeSetIcaoAddressV1()
                    }
                }.onFailure {
                    log.w { "Error parsing AIRCRAFT_CONFIGURATIONS_V2 ${it.message}" }
                }
                continue
            }
        }

        val elapsed = (timeSource.markNow() - markStart).inWholeMilliseconds
        log.i { "Sending ${returnedData.size} bytes, $numAircraft Aircraft, took %dms  to $sender ".format(elapsed) }
        return returnedData
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
