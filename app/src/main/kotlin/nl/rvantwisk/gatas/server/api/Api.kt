package nl.rvantwisk.gatas.server.api

import co.touchlab.kermit.Logger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import nl.rvantwisk.gatas.server.FLEET_CONFIG_KEY
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.extensions.toIPv4
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalStdlibApi::class, ExperimentalTime::class)
fun Application.configureApi() {

    val spatialService by inject<SpatialService>(named("SpatialService"))
    val log: Logger by inject { parametersOf(Application::class.simpleName!!) }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(RateLimit) {
        global {
            rateLimiter(limit = 60, refillPeriod = 60.seconds)
        }
    }

    routing {

        @Serializable
        data class AircraftReconfigure(val gatasId: Long, val newIcaoAdddress: Long)

        @Serializable
        data class GatasByPin(val lat: Double, val lon: Double, val pinCode: Int)

        post("/api/config/changeAircraft") {
            try {
                val config = call.receive<AircraftReconfigure>()
                spatialService.changeAircraft(config.gatasId, config.newIcaoAdddress.toUInt())
            } catch (e: Exception) {
                log.w { "Failed to change Aircraft config ${e.message}" }
            }
            call.respond(ApiResult.Ok(""))
        }

        /**
         * Handles GET requests to retrieve aircraft configuration details.
         *
         * This endpoint fetches specific configuration fields for a given GATAS ID.
         * It retrieves data such as options, unique ID, ICAO address(es), and GATAS IP.
         * The GATAS IP is converted to a standard IPv4 string format, and the ICAO address list
         * is parsed from a comma-separated string into a list of strings.
         *
         * @param gatasId The unique identifier of the GATAS unit, passed as a path parameter.
         * @return A JSON response containing the aircraft configuration details.
         *         Returns an error if the `gatasId` parameter is missing or invalid.
         */
        get("/api/config/aircraftConfiguration/{gatasId}") {
            val gatasId = call.parameters["gatasId"]?.toLong() ?: error("Missing aircraftId")
            val data = spatialService.getFieldsMap(
                FLEET_CONFIG_KEY,
                gatasId,
                listOf("options", "uniqueId", "icaoAddress", "icaoAddressList", "newIcaoAddress", "gatasIp")
            )
            if (data.isNotEmpty()) {
                val mData = data.toMutableMap()
                mData["gatasIp"] = (mData["gatasIp"] as Long).toIPv4();
                mData["icaoAddressList"] = mData["icaoAddressList"]?.toString()?.split(",")?.map { it.trim() }
                call.respond(ApiResult.Ok(mData.toJson()))
            } else {
                call.respond(ApiResult.Failed("Invalid"))
            }
        }

        post("/api/config/pinCode") {
            val timeSource = TimeSource.Monotonic
            val markStart = timeSource.markNow()
            try {
                val req = call.receive<GatasByPin>()

                if (req.pinCode !in 1000..999999) {
                    call.respond(ApiResult.Failed("Not in range"))
                    return@post
                }

                val aircraft = spatialService
                    .getFleetConfig(req.lat, req.lon, 200.0)
                    .firstOrNull { it.pinCode == req.pinCode }

                if (aircraft != null) {
                    call.respond(ApiResult.Ok(mapOf("gatasId" to aircraft.gatasId.toLong())))
                } else {
                    call.respond(ApiResult.Failed("Not found"))
                }
            } catch (e: Exception) {
                log.w { "Failed to handle by-pin request ${e.message}" }
                call.respond(ApiResult.Failed("Failed"))
            } finally {
                val elapsed = (timeSource.markNow() - markStart).inWholeMilliseconds
                log.i { "by-pin request took total ${elapsed}ms" }
            }
        }
    }
}

/**
 * Converts a Map<String, Any?> to a JsonObject.
 *
 * This extension function iterates over the map entries and constructs a JsonObject.
 * It supports common primitive types (String, Number, Boolean), null values, and lists
 * of these primitive types.
 *
 * Usecase: When getting data from a REDIS source as a map, this will create a JSON for a REST service
 *
 * @receiver The map to be converted to a JsonObject.
 * @return A [JsonObject] representation of the input map.
 * @throws IllegalArgumentException if an unsupported value type is encountered within the map
 *                                  or its lists.
 */
fun Map<String, Any?>.toJson(): JsonObject = buildJsonObject {
    for ((key, value) in this@toJson) {
        put(
            key, when (value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is List<*> -> JsonArray(
                    value.map { elem ->
                        when (elem) {
                            null -> JsonNull
                            is String -> JsonPrimitive(elem)
                            is Number -> JsonPrimitive(elem)
                            is Boolean -> JsonPrimitive(elem)
                            else -> error("Unsupported list element: $elem")
                        }
                    }
                )

                else -> error("Unsupported type: $value")
            })
    }
}

@Serializable
sealed class ApiResult<out T> {

    abstract val ok: Boolean

    @Serializable
    data class Ok<T>(
        val data: T
    ) : ApiResult<T>() {
        override val ok: Boolean = true
    }

    @Serializable
    data class Failed(
        val reason: String
    ) : ApiResult<Nothing>() {
        override val ok: Boolean = false
    }
}

