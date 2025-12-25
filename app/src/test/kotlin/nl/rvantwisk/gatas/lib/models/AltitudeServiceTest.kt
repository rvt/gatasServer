package nl.rvantwisk.gatas.lib.models

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import nl.rvantwisk.gatas.lib.services.AltitudeService
import nl.rvantwisk.gatas.lib.services.egm2008.EarthGravitationalModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

open class AltitudeServiceTest : KoinTest {

    val pos = AircraftPosition(
        id = 1u,
        dataSource = DataSource.ADSB,
        addressType = AddressType.ICAO,
        latitude = 52.0,
        longitude = 5.0,
        course = 0.0,
        hTurnRate = 0.0,
        groundSpeed = 0.0,
        verticalSpeed = 0.0,
        aircraftCategory = AircraftCategory.LIGHT_THAN_AIR,
        callSign = "TEST",
        qnh = null,
        nicBaro = 1,
        ellipsoidHeight = null,
        baroAltitude = null,
        isGround = false,
    )

    class FakeEgm2008Reader(private val offset: Int) : EarthGravitationalModel {
        override fun egmGeoidOffset(lat: Double, lon: Double): Int = offset
    }

    val altitudeService: AltitudeService by inject<AltitudeService>()


    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(module {
                single<EarthGravitationalModel>() { FakeEgm2008Reader(43) }
                single<AltitudeService>() { AltitudeService() }
            }
            )
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
    }


    @Test
    fun `_ellipsoidHeight Known`() {
        val pos = pos.copy(ellipsoidHeight = 1234)
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1013.25)
        Assertions.assertEquals(1234, pos.ellipsoidHeight)
    }

    @Test
    fun `At baro altitude 0ft, QNH std`() {
        val pos = pos.copy(baroAltitude = 0)
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1013.25)
        Assertions.assertEquals(40, pos.ellipsoidHeight)
    }

    @Test
    fun `At baro altitude ground, QNH std`() {
        val pos = pos.copy(baroAltitude = null, isGround = true)
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1013.25)
        Assertions.assertEquals(43, pos.ellipsoidHeight)
    }

    @Test
    fun `At baro altitude 1000ft, QNH std`() {
        val pos = pos.copy(baroAltitude = 1000) // 381Meter
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1013.25)
        Assertions.assertEquals(1040, pos.ellipsoidHeight)
    }

    @Test
    fun `At baro altitude 0ft, local QNH 1025`() {
        val pos = pos.copy(baroAltitude = 0)
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1025.0)
        Assertions.assertEquals(140 /* 43 + (1025-1013,25) * 8 ish */, pos.ellipsoidHeight)
    }

    @Test
    fun `At baro altitude 0ft, QNH 1025, aircraft QNH Ignored`() {
        val pos = pos.copy(baroAltitude = 0, qnh=888.0)
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1025.0)
        Assertions.assertEquals(140 /* 43 + (1025-1013,25) * 8 ish */, pos.ellipsoidHeight)
    }

    @Test
    fun `Nothing Known`() {
        altitudeService.updateEstimGeomAltitude(pos, localQnh = 1025.0)
        Assertions.assertEquals(-9999, pos.ellipsoidHeight)
    }

    @Test
    fun `Calculates Pressure Altitude 1026`() {
        val pressureAltitude = altitudeService.getCorrectedAltitude(0, localQnh = 1013.25 + 13.25)
        Assertions.assertEquals(109.445 , pressureAltitude, 0.01)
    }

    @Test
    fun `Calculates Pressure Altitude 1013`() {
        val pressureAltitude = altitudeService.getCorrectedAltitude(0, localQnh = 1013.25)
        Assertions.assertEquals(0.0, pressureAltitude, 0.01)
    }

    @Test
    fun `Calculates Pressure Altitude 1000`() {
        val altitude = altitudeService.getCorrectedAltitude(0, localQnh = 1013.25 - 13.25)
        Assertions.assertEquals(-111.16, altitude, 0.01)
    }

}
