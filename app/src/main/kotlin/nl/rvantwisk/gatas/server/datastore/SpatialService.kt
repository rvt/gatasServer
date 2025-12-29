package nl.rvantwisk.gatas.server.datastore

import co.touchlab.kermit.Logger
import com.uber.h3core.H3Core
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.output.ValueOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import nl.rvantwisk.gatas.lib.extensions.ALTITUDE
import nl.rvantwisk.gatas.lib.extensions.DATA_SOURCE
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.models.OwnshipAircraftConfiguration
import nl.rvantwisk.gatas.lib.models.OwnshipPosition
import nl.rvantwisk.gatas.server.*
import nl.rvantwisk.gatas.server.datastore.tile38.FSET
import nl.rvantwisk.gatas.server.datastore.tile38.GET
import nl.rvantwisk.gatas.server.datastore.tile38.NEARBY
import nl.rvantwisk.gatas.server.datastore.tile38.SCAN
import nl.rvantwisk.gatas.server.datastore.tile38.models.*
import nl.rvantwisk.gatas.server.metar.model.Metar
import nl.rvantwisk.gatas.server.metar.model.toH3
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import kotlin.time.ExperimentalTime


class SpatialService : KoinComponent {
    private val log: Logger by inject { parametersOf(SpatialService::class.simpleName!!) }
    private val redisClientRead: StatefulRedisConnection<String, String> by inject(named("tile38ReadConnection"))
    private val redisClientWrite: StatefulRedisConnection<String, String> by inject(named("tile38writeConnection"))
    private val h3: H3Core by inject()

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        val codec = StringCodec()
    }


    private fun keyIdCmd(key: String, id: Any): CommandArgs<String, String> {
        return CommandArgs(StringCodec.UTF8).apply {
            add(key)
            when (id) {
                is Int -> add(id.toLong())
                is Long -> add(id)
                is UInt -> add(id.toLong())
                is String -> add(id)
                else -> error("Unsupported id type: ${id::class.simpleName}")
            }
        }
    }

    /**
     * Send aircraft to Tile38
     * @param aircraftList list of aircraft
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalStdlibApi::class)
    suspend fun storeAircraft(aircraftList: List<AircraftPosition>) = coroutineScope {
        log.i { "Pushing ${aircraftList.size} aircraft to Tile" }

        aircraftList.map { aircraft ->

            val storeAlt =
                ((if (aircraft.isGround)
                    0
                else
                    if (aircraft.ellipsoidHeight != null)
                        aircraft.ellipsoidHeight
                    else
                        aircraft.baroAltitude) ?: -999).toLong()

            async {
                val cmdArgs = keyIdCmd(AIRCRAFT_KEY, aircraft.id)
                    .add("POINT").add(aircraft.latitude).add(aircraft.longitude)
                    .add(storeAlt)
                    .add("EX").add(STORE_AIRCRAFT_EXPIRE_SECONDS)
                    .add("FIELD").add(ALTITUDE).add(storeAlt)
                    .add("FIELD").add(UBER_H3)
                    .add(h3.latLngToCell(aircraft.latitude, aircraft.longitude, H3_AIRCRAFT_CELL_SIZE))
                    .add("FIELD").add(GROUND).add(if (aircraft.isGround) 1 else 0)
                    .add("FIELD").add("json").add(json.encodeToString(AircraftPosition.serializer(), aircraft))

                redisClientWrite.coroutines()
                    .dispatch(CommandType.SET, StatusOutput(StringCodec.UTF8), cmdArgs)
                    .collect()
            }
        }.awaitAll()
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

        val cmdArgs = CommandArgs(codec)
            .add(AIRCRAFT_KEY)
            .add("WHERE").add(ALTITUDE).add((ellipsoidHeight - STORE_FILTER_BELOW_OWNSHIP).toLong())
            .add((ellipsoidHeight + STORE_FILTER_ABOVE_OWNSHIP).toLong())
            .add("LIMIT").add(STORE_MAX_AIRCRAFT)
            .add("POINT").add(lat).add(lon)
            .add(STORE_MAX_RADIUS)

        val response = coroutines.dispatch(
            NEARBY(),
            ValueOutput(codec),
            cmdArgs
        ).toList()

        return json.decodeFromString<Tile38NearbyResult>(response.first()).fromJsonField<AircraftPosition>()
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

        val cmdArgs = CommandArgs(codec).add(FLEET_KEY).add("LIMIT").add(limit)

        val response = coroutines.dispatch(
            SCAN(),
            ValueOutput(codec),
            cmdArgs
        ).toList()
        return json.decodeFromString<Tile38NearbyResult>(response.first()).fromJsonField<OwnshipPosition>()
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
                val cmdArgs = keyIdCmd(FLEET_KEY, ownship.id)
                    .add("POINT").add(ownship.latitude).add(ownship.longitude)
                    .add("EX").add(STORE_FLEET_EXPIRE_SECONDS)
                    .add("FIELD").add(UBER_H3)
                    .add(h3.latLngToCell(ownship.latitude, ownship.longitude, H3_AIRCRAFT_CELL_SIZE))
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

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
    suspend fun addMetar(metar: Metar) {
        addMetarByKey(
            metar.toH3(),
            METAR_BY_H3_KEY,
            h3.latLngToCell(metar.latitude, metar.longitude, H3_AIRCRAFT_CELL_SIZE)
        )
        addMetarByKey(metar.toH3(), METAR_BY_STATION_KEY, metar.station_id)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
    suspend fun addMetarByH3(metar: MetarH3, h3: Long) {
        addMetarByKey(metar, METAR_BY_H3_KEY, h3)
    }

    /**
     * Store a metar in the database under a specific key
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
    private suspend fun addMetarByKey(metar: MetarH3, key: String, id: Any) {
        val coroutines = redisClientWrite.coroutines()
        try {
            val cmdArgs = keyIdCmd(key, id)
                .add("POINT").add(metar.latitude).add(metar.longitude)
                // Worst case scenario is that cache from h3 is 2 minutes older then a metar by ID
                // But will be evicted much more frequent
                .add("EX").add(if (key == METAR_BY_H3_KEY) 300 else STORE_METAR_EXPIRE_SECONDS)
                // .add("FIELD").add("elevation").add(metar.elevation_m)
                .add("FIELD").add("qnh").add(metar.qnh)
                // .add("FIELD").add("otime").add(metar.observation_time.toString())
                .add("FIELD").add("json").add(json.encodeToString(metar))
            coroutines.dispatch(
                CommandType.SET,
                StatusOutput(codec),
                cmdArgs
            ).collect()
        } catch (e: Exception) {
            log.w { "Error sending metar to Tile38: ${e.message}" }
        }
    }


    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class, ExperimentalSerializationApi::class)
    suspend fun getMetarByH3(h3Id: Long): MetarH3? {
        val coroutines = redisClientRead.coroutines()

        val response = coroutines.dispatch(
            GET(),
            ValueOutput(codec),
            keyIdCmd(METAR_BY_H3_KEY, h3Id).add("WITHFIELDS")
        ).toList()

        val result = json.decodeFromString<Tile38GetResult>(response.first())

        return if (result.ok) {
            result.fromJsonField<MetarH3>()
        } else {
            null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
    suspend fun getNearbyMetar(
        lat: Double,
        lon: Double
    ): List<MetarH3> {
        val coroutines = redisClientRead.coroutines()

        val cmdArgs = CommandArgs(codec)
            .add(METAR_BY_STATION_KEY)
            .add("LIMIT").add(5)
            .add("POINT").add(lat).add(lon)
            .add(STORE_METAR_MAX_RADIUS)

        val response = coroutines.dispatch(
            NEARBY(),
            ValueOutput(codec),
            cmdArgs
        ).toList()

        return json.decodeFromString<Tile38NearbyResult>(response.first())
            .fromJsonField<MetarH3>()
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
    suspend fun getNearbyQNH(
        lat: Double,
        lon: Double
    ): Double? {
        val coroutines = redisClientRead.coroutines()
        val cmdArgs = CommandArgs(codec)
            .add(METAR_BY_STATION_KEY)
            .add("LIMIT").add(5)
            .add("POINT").add(lat).add(lon)
            .add(STORE_METAR_MAX_RADIUS)

        val response = coroutines.dispatch(
            NEARBY(),
            ValueOutput(codec),
            cmdArgs
        ).toList()

        val result = json.decodeFromString<Tile38NearbyResult>(response.first())

        return if (result.ok) {
            result.fromJsonField<Double>("qnh")
                .firstOrNull()
        } else {
            null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun setFleetConfig(
        lat: Double,
        lon: Double,
        data: OwnshipAircraftConfiguration,
    ) {
        val coroutines = redisClientWrite.coroutines()

        val cmdArgs = keyIdCmd(FLEET_CONFIG_KEY, data.gatasId)
            .add("POINT").add(lat).add(lon)
            .add("FIELD").add("options").add(data.options.toLong())
            .add("FIELD").add("uniqueId").add(data.gatasId.toLong())
            .add("FIELD").add("gatasIp").add(data.gatasIp.toLong())
            .add("FIELD").add("pinCode").add(data.pinCode.toLong())
            .add("FIELD").add("version").add(data.version.toLong())
            .add("FIELD").add("icaoAddress").add(data.icaoAddress.toLong())
            .add("FIELD").add("icaoAddressList").add(data.icaoAddressList.joinToString(","))
            .add("FIELD").add(DATA_SOURCE).add("gatas")
            .add("FIELD").add("json").add(json.encodeToString(OwnshipAircraftConfiguration.serializer(), data))

        coroutines.dispatch(
            CommandType.SET /*FSET()*/, StatusOutput(StringCodec.UTF8), cmdArgs
        ).toList()
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
    suspend fun getFleetConfig(
        lat: Double,
        lon: Double,
        maxRadius: Double
    ): List<OwnshipAircraftConfiguration> {
        val coroutines = redisClientRead.coroutines()

        val cmdArgs = CommandArgs(codec)
            .add(FLEET_CONFIG_KEY)
            .add("LIMIT").add(16)
            .add("POINT").add(lat).add(lon)
            .add(maxRadius)

        val response = coroutines.dispatch(
            NEARBY(),
            ValueOutput(codec),
            cmdArgs
        ).toList()

        val result =
            json.decodeFromString<Tile38NearbyResult>(response.first()).fromJsonField<OwnshipAircraftConfiguration>()
        return result
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun changeAircraft(gatasId: Long, newIcaoAdddress: UInt) {
        val coroutines = redisClientWrite.coroutines()
        try {
            val cmdArgs = keyIdCmd(FLEET_CONFIG_KEY, gatasId)
                .add("newIcaoAddress").add(newIcaoAdddress.toLong())

            coroutines.dispatch(
                FSET(),
                StatusOutput(StringCodec.UTF8),
                cmdArgs
            ).toList()
        } catch (e: Exception) {
            log.w { "Error sending metar to Tile38: ${e.message}" }
        }
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

        val response = redisClientRead.coroutines().dispatch(
            GET(),
            ValueOutput(codec),
            keyIdCmd(key, id).add("WITHFIELDS")
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

        val cmdArgs = keyIdCmd(key, id).add("WITHFIELDS")

        val response = redisClientRead.coroutines().dispatch(
            GET(),
            ValueOutput(codec),
            cmdArgs
        ).firstOrNull()

        if (response == null) {
            return null
        }
        val result = json.decodeFromString<Tile38ObjectResult>(response)

        if (result.fields == null) {
            return null
        }
        val fieldElement = result.fields.jsonObject[field] ?: return null

        return json.decodeFromJsonElement<T>(fieldElement)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
    internal suspend inline fun setFieldsMap(
        key: String,
        id: Any,
        lat: Double, lon: Double,
        fields: Map<String, Any?>
    ): Boolean {

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


}
