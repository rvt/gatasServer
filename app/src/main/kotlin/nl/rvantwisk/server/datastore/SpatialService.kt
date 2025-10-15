package nl.rvantwisk.server.datastore

import co.touchlab.kermit.Logger
import com.uber.h3core.H3Core
import io.github.kotlin.fibonacci.nl.rvantwisk.gatas.models.OwnshipAircraftConfiguration
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.output.ValueOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import nl.rvantwisk.gatas.extensions.ALTITUDE
import nl.rvantwisk.gatas.extensions.DATA_SOURCE
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.OwnshipPosition
import nl.rvantwisk.server.H3_RESOLUTION
import nl.rvantwisk.server.STORE_AIRCRAFT_EXPIRE_SECONDS
import nl.rvantwisk.server.STORE_FILTER_ABOVE_OWNSHIP
import nl.rvantwisk.server.STORE_FILTER_BELOW_OWNSHIP
import nl.rvantwisk.server.STORE_FLEET_EXPIRE_SECONDS
import nl.rvantwisk.server.STORE_MAX_AIRCRAFT
import nl.rvantwisk.server.STORE_MAX_RADIUS
import nl.rvantwisk.server.STORE_METAR_EXPIRE_SECONDS
import nl.rvantwisk.server.STORE_METAR_MAX_RADIUS
import nl.rvantwisk.server.UBER_H3
import nl.rvantwisk.server.datastore.tile38.FSET
import nl.rvantwisk.server.datastore.tile38.GET
import nl.rvantwisk.server.datastore.tile38.NEARBY
import nl.rvantwisk.server.datastore.tile38.SCAN
import nl.rvantwisk.server.datastore.tile38.models.Tile38NearbyResult
import nl.rvantwisk.server.datastore.tile38.models.Tile38ObjectResult
import nl.rvantwisk.server.datastore.tile38.models.listOf
import nl.rvantwisk.server.metar.model.Metar
import nl.rvantwisk.server.metar.model.MetarH3
import nl.rvantwisk.server.metar.model.toH3
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.time.ExperimentalTime


class SpatialService : KoinComponent {
  private val log: Logger by inject { parametersOf(SpatialService::class.simpleName!!) }

  private val redisClientRead: StatefulRedisConnection<String, String> by inject(named("tile38ReadConnection"))
  private val redisClientWrite: StatefulRedisConnection<String, String> by inject(named("tile38writeConnection"))

  private val json = Json { ignoreUnknownKeys = true }
  private val h3: H3Core by inject()
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


  // Convert object → Map<String, Any?>
  fun <T : Any> toMap(obj: T): Map<String, Any?> {
    return obj::class.memberProperties.associate { prop ->
      prop.name to prop.getter.call(obj)
    }
  }

  // Convert Map<String, Any?> → Object
  fun <T : Any> fromMap(map: Map<String, Any?>, clazz: Class<T>): T {
    val ctor = clazz.kotlin.primaryConstructor
      ?: throw IllegalArgumentException("Class must have a primary constructor")
    val args = ctor.parameters.associateWith { param -> map[param.name] }
    return ctor.callBy(args)
  }

  private fun keyIdCmd(key: String, id: Any): CommandArgs<String, String> {
    return CommandArgs(StringCodec.UTF8).apply {
      add(key)
      when (id) {
        is Integer -> add(id.toLong())
        is Long -> add(id)
        is UInt -> add(id.toLong())
        is String -> add(id)
        else -> error("Unsupported id type: ${id::class.simpleName}")
      }
    }
  }

  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalStdlibApi::class)
  fun sendAircrafts(aircraftList: List<AircraftPosition>) {
    log.i { "Pushing to Tile ${aircraftList.size} aircraft" }
    val coroutines = redisClientWrite.coroutines()

    aircraftList.map { aircraft ->
      scope.async {
        // @formatter:off
        val cmdArgs = keyIdCmd("aircraft", aircraft.id)
        .add("POINT").add(aircraft.latitude).add(aircraft.longitude).add(aircraft.ellipsoidHeight.toLong())
        .add("EX").add(STORE_AIRCRAFT_EXPIRE_SECONDS)
        .add("FIELD").add(UBER_H3).add(h3.latLngToCell(aircraft.latitude, aircraft.longitude, H3_RESOLUTION))
        .add("FIELD").add(ALTITUDE).add(aircraft.ellipsoidHeight.toLong())
        .add("FIELD").add("json").add(json.encodeToString(AircraftPosition.serializer(), aircraft))

    // @formatter:on
        coroutines.dispatch(
          CommandType.SET,
          StatusOutput(StringCodec.UTF8),
          cmdArgs
        ).collect() // suspend
      }
    }
  }

  /**
   * Return sll aircraft around the ownship location from Tile38
   */
  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  suspend fun getAircraft(
    lat: Double,
    lon: Double,
    ellipsoidHeight: Int
  ): List<AircraftPosition> {
    val coroutines = redisClientRead.coroutines()
    val codec = StringCodec()

    // @formatter:off
    val cmdArgs = CommandArgs(codec)
    .add("aircraft")
    .add("WHERE").add(ALTITUDE).add((ellipsoidHeight - STORE_FILTER_BELOW_OWNSHIP).toLong()).add((ellipsoidHeight + STORE_FILTER_ABOVE_OWNSHIP).toLong())
    .add("LIMIT").add(STORE_MAX_AIRCRAFT)
    .add("POINT").add(lat).add(lon)
    .add(STORE_MAX_RADIUS)
    // @formatter:on

    val response = coroutines.dispatch(
      NEARBY(),
      ValueOutput(codec),
      cmdArgs
    ).toList()

    return json.decodeFromString<Tile38NearbyResult>(response.first()).listOf<AircraftPosition>()
  }

  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  /**
   * Scan the fleet from Tile38
   * @param limit number of records to return
   * @return list of ownships
   */
  suspend fun scanFleet(
    limit: Long = 1
  ): List<OwnshipPosition> {
    val coroutines = redisClientRead.coroutines()
    val codec = StringCodec()

    // @formatter:off
      val cmdArgs = CommandArgs(codec)
        .add("fleet")
        .add("LIMIT").add(limit)
    // @formatter:on

    val response = coroutines.dispatch(
      SCAN(),
      ValueOutput(codec),
      cmdArgs
    ).toList()
    return json.decodeFromString<Tile38NearbyResult>(response.first()).listOf<OwnshipPosition>()
  }

  /**
   * Send required ownship data to Tile38
   */
  @OptIn(ExperimentalLettuceCoroutinesApi::class)
  fun updateOwnship(
    ownship: OwnshipPosition
  ) {
    val coroutines = redisClientWrite.coroutines()
    scope.launch {
      runCatching {
        val cmdArgs = keyIdCmd("fleet", ownship.id)
          .add("POINT").add(ownship.latitude).add(ownship.longitude)
          .add("EX").add(STORE_FLEET_EXPIRE_SECONDS)
          .add("FIELD").add(UBER_H3).add(h3.latLngToCell(ownship.latitude, ownship.longitude, H3_RESOLUTION))
          .add("FIELD").add(DATA_SOURCE).add("gatas")
          .add("FIELD").add("json").add(json.encodeToString(OwnshipPosition.serializer(), ownship))

        coroutines.dispatch(
          CommandType.SET,
          StatusOutput(StringCodec.UTF8),
          cmdArgs
        ).collect()
      }
    }
  }


  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  suspend fun getNearbyMetar(
    lat: Double,
    lon: Double
  ): List<MetarH3> {
    val coroutines = redisClientRead.coroutines()
    val codec = StringCodec()

    // @formatter:off
    val cmdArgs = CommandArgs(codec)
      .add("metar")
      .add("LIMIT").add(5)
      .add("POINT").add(lat).add(lon)
      .add(STORE_METAR_MAX_RADIUS)
    // @formatter:on

    val response = coroutines.dispatch(
      NEARBY(),
      ValueOutput(codec),
      cmdArgs
    ).toList()

    return json.decodeFromString<Tile38NearbyResult>(response.first()).listOf<MetarH3>()
  }


  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
  suspend fun addMetar(metar: Metar) {
    addMetarById(metar.toH3(), "metarh3", h3.latLngToCell(metar.latitude!!, metar.longitude!!, H3_RESOLUTION))
    addMetarById(metar.toH3(), "metar", metar.station_id)
  }

  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
  suspend fun addMetarById(metar: MetarH3, key: String, id: Any) {
    val coroutines = redisClientWrite.coroutines()
    try {
      val cmdArgs = keyIdCmd(key, id)
        .add("POINT").add(metar.latitude).add(metar.longitude)
        // Worst case scenario is that cache from h3 is 2 minutes older then a metar by ID
        // But will be evicted much more frequent
        .add("EX").add(if (key == "metarh3") 300 else STORE_METAR_EXPIRE_SECONDS)
        .add("FIELD").add("elevation").add(metar.elevation_m)
        .add("FIELD").add("qnh").add(metar.qnh)
        .add("FIELD").add("otime").add(metar.observation_time.toString())
        .add("FIELD").add("json").add(json.encodeToString(metar))
      coroutines.dispatch(
        CommandType.SET,
        StatusOutput(StringCodec.UTF8),
        cmdArgs
      ).collect()
    } catch (e: Exception) {
      log.w { "Error sending metar to Tile38: ${e.message}" }
    }
  }


  @OptIn(ExperimentalLettuceCoroutinesApi::class)
  fun sendAircraftConfig(
    lat: Double,
    lon: Double,
    data: OwnshipAircraftConfiguration,
  ) {
    val coroutines = redisClientWrite.coroutines()
    scope.launch {
      var cmdArgs = keyIdCmd("fleetConfig", data.gatasId)
        .add("NX")
        .add("POINT").add(lat).add(lon)
      val result = coroutines.dispatch(
        CommandType.SET, StatusOutput(StringCodec.UTF8), cmdArgs
      ).toList()

      cmdArgs = keyIdCmd("fleetConfig", data.gatasId)
        .add("options").add(data.options.toLong())
        .add("uniqueId").add(data.gatasId.toLong())
        .add("gatasIp").add(data.gatasIp.toLong())
        .add("icaoAddress").add(data.icaoAddress.toLong())
        .add("icaoAddressList").add(data.icaoAddressList.joinToString(","))
        .add(DATA_SOURCE).add("gatas")
//        .add("FIELD").add("json").add(json.encodeToString(OwnshipAircraftConfiguration.serializer(), data))

      coroutines.dispatch(
        FSET(), StatusOutput(StringCodec.UTF8), cmdArgs
      ).toList()
    }
  }

  @OptIn(ExperimentalLettuceCoroutinesApi::class)
  suspend fun changeAircraft(gatasId: Long, newIcaoAdddress: UInt) {
    val coroutines = redisClientWrite.coroutines()
    try {
      val cmdArgs = keyIdCmd("fleetConfig", gatasId)
        .add("newIcaoAddress").add(newIcaoAdddress.toLong())

      val ok = coroutines.dispatch(
        FSET(),
        StatusOutput(StringCodec.UTF8),
        cmdArgs
      ).toList()
      print(ok)
    } catch (e: Exception) {
      log.w { "Error sending metar to Tile38: ${e.message}" }
    }
  }


  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  internal suspend inline fun setFieldsMap(
    key: String,
    id: Any,
    lat: Double, lon: Double,
    fields: Map<String, Any?>
  ): Boolean {
    val codec = StringCodec()

    // @formatter:off
    val cmdArgs = keyIdCmd(key, id)
      .add("POINT").add(lat).add(lon)
    fields.forEach { (fieldName, fieldValue) ->
      if (fieldValue!=null) {
        cmdArgs.add("FIELD")
        cmdArgs.add(fieldName)
        cmdArgs.add(fieldValue.toString())
      }
    }
    // @formatter:on

    val response = redisClientWrite.coroutines().dispatch(
      CommandType.SET,
      StatusOutput(codec),
      cmdArgs
    )
    return response.toList().first() == "OK"
  }

  /**
   * Get a number of fields as a Map<String, Any?> from Tile38
   */
  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  internal suspend inline fun getFieldsMap(
    key: String,
    id: Any,
    fields: List<String>
  ): Map<String, Any?> {
    val codec = StringCodec()

    val cmdArgs = keyIdCmd(key, id)
      .add("WITHFIELDS")

    val response = redisClientRead.coroutines().dispatch(
      GET(),
      ValueOutput(codec),
      cmdArgs
    ).firstOrNull()

    if (response == null) return emptyMap()
    val result = json.decodeFromString<Tile38ObjectResult>(response)

    if (result.fields == null) {
      return emptyMap()
    }

    return fields.associateWith { id -> result.fields.jsonObject[id]?.toKotlinValue() }
  }

  fun JsonElement.toKotlinValue(): Any? = when {
    this is JsonNull -> null
    this is JsonPrimitive && this.isString -> this.content
    this is JsonPrimitive && this.booleanOrNull != null -> this.boolean
    this is JsonPrimitive && this.intOrNull != null -> this.int
    this is JsonPrimitive && this.longOrNull != null -> this.long
    this is JsonPrimitive && this.doubleOrNull != null -> this.double
    else -> this.toString() // fallback for JsonObject/JsonArray or unknown
  }


  /**
   * Deserialize a field from Tile38 into a JSON object
   */
  @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
  internal suspend inline fun <reified T> getFieldAs(
    key: String,
    id: Any,
    field: String
  ): T? {
    val codec = StringCodec()

    // @formatter:off
    val cmdArgs = keyIdCmd(key, id)
      .add("WITHFIELDS")
    // @formatter:on

    val response = redisClientRead.coroutines().dispatch(
      GET(),
      ValueOutput(codec),
      cmdArgs
    ).firstOrNull()

    if (response == null) return null
    val result = json.decodeFromString<Tile38ObjectResult>(response)

    if (result.fields == null) {
      return null
    }
    val fieldElement = result.fields.jsonObject[field] ?: return null

    return json.decodeFromJsonElement<T>(fieldElement)
  }


}
