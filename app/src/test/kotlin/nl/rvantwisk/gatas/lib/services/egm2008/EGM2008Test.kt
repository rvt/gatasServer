package nl.rvantwisk.gatas.lib.services.egm2008

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.mockk
import nl.rvantwisk.gatas.lib.utils.readByteArray
import nl.rvantwisk.gatas.server.TimestampLogWriter
import nl.rvantwisk.gatas.server.datastore.SpatialService
import okio.Path.Companion.toPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EGM2008Test : KoinTest {


    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @BeforeEach
    fun setupKoin() {

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

                single<EarthGravitationalModel>() {
                    val egm = EGM2008()
                    egm.init(readByteArray("./egm2008.bin".toPath()))
                    egm
                }

            })
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `EGM2008 get Height`() {
        val egm = get<EarthGravitationalModel>()
        assertEquals(-14, egm.egmGeoidOffset(lat = 40.015, lon = -105.2705))
        assertEquals(-28, egm.egmGeoidOffset(lat = 27.98833, lon = 86.92528))
        assertEquals(-107, egm.egmGeoidOffset(lat = 5.0, lon = 79.0))
        assertEquals(45, egm.egmGeoidOffset(lat = 51.0, lon = 4.0))
    }
}
