package nl.rvantwisk.gatas.it

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.uber.h3core.H3Core
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import nl.rvantwisk.gatas.lib.models.OwnshipAircraftConfiguration
import nl.rvantwisk.gatas.server.*
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.datastore.tile38.DROP
import nl.rvantwisk.gatas.server.datastore.tile38.models.MetarH3
import nl.rvantwisk.gatas.server.datastore.tile38.setJsonOutput
import nl.rvantwisk.gatas.server.metar.model.Metar
import nl.rvantwisk.gatas.server.metar.model.toH3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

open class SpatialServiceITest : Tile38BaseIT(), KoinTest {

    val spatialService: SpatialService by inject<SpatialService>(named("SpatialService"))
    val redisClientWrite: StatefulRedisConnection<String, String> by inject(named("tile38writeConnection"))
    val h3: H3Core by inject()


    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @BeforeEach
    fun setupKoin() {
        val mappedPort = tile38.getMappedPort(TILE38_PORT)
        val uri = RedisURI.Builder.redis(tile38.host, mappedPort).build()

        startKoin {
            modules(module {
                single<Logger>(named("Logger")) {
                    Logger(
                        config = StaticConfig(
                            minSeverity = Severity.Info,
                            logWriterList = listOf(TimestampLogWriter)
                        )
                    )
                }
                factory { (tag: String) -> get<Logger>(named("Logger")).withTag(tag) }

                single<SpatialService>(named("SpatialService")) { SpatialService() }

                single<H3Core> { H3Core.newInstance() }
                single<StatefulRedisConnection<String, String>>(named("tile38ReadConnection")) {
                    val connection = RedisClient.create(uri).connect()
                    runBlocking {
                        connection.setJsonOutput()

                    }
                    connection
                }

                single<StatefulRedisConnection<String, String>>(named("tile38writeConnection")) {
                    val connection = RedisClient.create(uri).connect()
                    runBlocking {
                        connection.setJsonOutput()

                    }
                    connection
                }
            })
        }

        // @formatter:off
        runBlocking {
            for (key in listOf(METAR_BY_H3_KEY, METAR_BY_STATION_KEY, AIRCRAFT_KEY, FLEET_CONFIG_KEY, FLEET_KEY)) {
                redisClientWrite.coroutines().dispatch( DROP(),
                    StatusOutput(StringCodec.UTF8),
                    CommandArgs(StringCodec()).add(key)).collect()
            }
        }
        // @formatter:on
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        val metar = Metar(
            station_id = "EHAM",
            latitude = 52.31,
            longitude = 4.76,
            elevation_m = 100,
            sea_level_pressure_mb = 1012.12,
            observation_time = Instant.Companion.parse("2023-09-01T12:00:00Z"),
            raw_text = "",
            altim_in_hg = 0.0
        )
    }

    @Test
    fun `test connection to tile38`() {
        assertTrue(tile38.isRunning, "Tile38 container should be running")
        val mappedPort = tile38.getMappedPort(TILE38_PORT)
        assertTrue(mappedPort > 0, "Mapped port should be a positive integer")
    }


    @OptIn(ExperimentalTime::class)
    @Test
    fun `Should store metar and allow retrieval`() {
        runBlocking {
            spatialService.addMetar(metar)
            val fields = spatialService.getNearbyMetar(52.0, 4.0)

            val expected = MetarH3(
                id = "EHAM",
                latitude = 52.31,
                longitude = 4.76,
                qnh = 1012.12,
                elevation = 100,
                observationTime = Instant.Companion.parse("2023-09-01T12:00:00Z")
            )
            assertEquals(expected, fields.first())

            assertEquals(1012.12, spatialService.getNearbyQNH(52.0, 4.0))
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Should store and get MetarH3 by H3id`() {
        runBlocking {
            val h3Id = h3.latLngToCell(51.0, 4.0, H3_AIRCRAFT_CELL_SIZE)
            spatialService.addMetarByH3(metar.toH3(), h3Id)

            val metarh3 = spatialService.getMetarByH3(h3Id)
            val expected = MetarH3(
                id = "EHAM",
                latitude = 52.31,
                longitude = 4.76,
                qnh = 1012.12,
                elevation = 100,
                observationTime = Instant.Companion.parse("2023-09-01T12:00:00Z")
            )
            assertEquals(expected, metarh3)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Should return null when H3 not found`() {
        runBlocking {
            val h3Id = h3.latLngToCell(51.0, 4.0, H3_AIRCRAFT_CELL_SIZE)

            assertNull(spatialService.getMetarByH3(h3Id))
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Store and Fetch OwnshipConfig`() {
        runBlocking {
            var data = OwnshipAircraftConfiguration(
                gatasId = 12345.toUInt(),
                options = 0.toUInt(),
                icaoAddress = 312134.toUInt(),
                newIcaoAddress = null,
                icaoAddressList = listOf(1.toUInt(), 2.toUInt()),
                gatasIp = 67890.toUInt(),
                pinCode = 1234,
                version = 0,
            )

            // Initital set
            spatialService.setFleetConfig(54.0, 4.0, data)
            var result = spatialService.getFleetConfig(54.0, 4.0, 100.0)
            assertEquals(1, result.size)
            assertEquals(data, result.first())

            // Update data and position
            data = data.copy(icaoAddress = 12134.toUInt())
            spatialService.setFleetConfig(54.0, 5.0, data)
            result = spatialService.getFleetConfig(54.0, 5.0, 100.0)
            assertEquals(data, result.first())
        }
    }
}
