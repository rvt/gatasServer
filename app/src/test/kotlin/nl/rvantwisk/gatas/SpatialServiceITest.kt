package com.example

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
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import nl.rvantwisk.gatas.Tile38BaseIT
import nl.rvantwisk.gatas.lib.extensions.DATA_SOURCE
import nl.rvantwisk.gatas.server.AIRCRAFT_KEY
import nl.rvantwisk.gatas.server.FLEET_CONFIG_KEY
import nl.rvantwisk.gatas.server.FLEET_KEY
import nl.rvantwisk.gatas.server.H3_AIRCRAFT_CELL_SIZE
import nl.rvantwisk.gatas.server.METAR_BY_H3_KEY
import nl.rvantwisk.gatas.server.METAR_BY_STATION_KEY
import nl.rvantwisk.gatas.server.TimestampLogWriter
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.datastore.tile38.DROP
import nl.rvantwisk.gatas.server.datastore.tile38.FSET
import nl.rvantwisk.gatas.server.datastore.tile38.models.MetarH3
import nl.rvantwisk.gatas.server.datastore.tile38.setJsonOutput
import nl.rvantwisk.gatas.server.metar.model.Metar
import nl.rvantwisk.gatas.server.metar.model.toH3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.koin.core.component.inject
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
            observation_time = Instant.parse("2023-09-01T12:00:00Z"),
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
                observationTime = Instant.parse("2023-09-01T12:00:00Z")
            )
            assertEquals(expected, fields.first())
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
                observationTime = Instant.parse("2023-09-01T12:00:00Z")
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
}
