package nl.rvantwisk.server.api

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import nl.rvantwisk.server.datastore.SpatialService
import nl.rvantwisk.server.extensions.toIPv4
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@OptIn(ExperimentalStdlibApi::class)
fun Application.configureApi() {

  val spatialService by inject<SpatialService>(named("SpatialService"))

  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      ignoreUnknownKeys = true
      encodeDefaults = true
    })
  }


  routing {

    @Serializable
    data class AircraftReconfigure(val gatasId: Long, val newIcaoAdddress: Long)

    post("/api/config/changeAircraft") {
      try {
        val config = call.receive<AircraftReconfigure>()
        spatialService.changeAircraft(config.gatasId, config.newIcaoAdddress.toUInt())
      } catch (e: Exception) {
        print(e)
      }
      call.respond(Ok())
    }

    get("/api/config/aircraftConfiguration/{gatasId}") {
      val gatasId = call.parameters["gatasId"]?.toLong() ?: error("Missing aircraftId")
      val data = spatialService.getFieldsMap(
        "fleetConfig",
        gatasId,
        listOf("options", "uniqueId", "icaoAddress", "icaoAddressList", "newIcaoAddress", "gatasIp")
      )
      val mData = data.toMutableMap()
      mData["gatasIp"] = (mData["gatasIp"] as Long).toIPv4();
      mData["icaoAddressList"] = mData["icaoAddressList"]?.toString()?.split(",")?.map { it.trim() }
      call.respond(mData.toJson())
    }

//    get("/config/{aircraftId}") {
//      val aircraftId = call.parameters["aircraftId"] ?: error("Missing aircraftId")
//      val data = spatialService.getFieldAs<OwnshipAircraftConfiguration>("fleetConfig", aircraftId, "json")
//
//      call.respond(ThymeleafContent("config", mapOf("config" to data)))
//    }
  }
}

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
data class Ok(val status: String = "Ok")

