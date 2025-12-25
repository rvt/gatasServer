package nl.rvantwisk.gatas.server.flowservices

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.rvantwisk.gatas.lib.models.*
import nl.rvantwisk.gatas.lib.webservice.AircraftWebService
import nl.rvantwisk.gatas.server.TimestampLogWriter
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.flowservices.FakeWebService.Companion.DELAY_BEFORE_COLLECTION
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals

class FakeWebService(
    private val name: String,
    private val maxRadius: Double,
    private val timeout: Boolean = false,
    private val fails: Boolean = false,
) : AircraftWebService {
    companion object {
        val DELAY_BEFORE_COLLECTION: Long = 500;
    }

    var callCount = 0
    override fun getName() = name
    override fun getMaxRadius() = maxRadius

    override suspend fun fetchPositions(
        latitude: Double,
        longitude: Double,
        radiusM: Double
    ): List<AircraftPosition> {
        if (timeout) {
            throw CancellationException("Simulated timeout")
        }
        if (fails) {
            throw RuntimeException("Service Failure")
        }
        callCount++
        return emptyList()
    }
}

class ClusterDispatcherTest : KoinTest {
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

                single<SpatialService>(named("SpatialService")) {
                    mockk<SpatialService>(relaxed = true)
                }

            })
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
    }

    val ownshipPos = OwnshipPosition(
        epoch = 0,
        id = 0,
        latitude = 0.0,
        longitude = 0.0,
        addressType = AddressType.ICAO,
        category = AircraftCategory.LIGHT,
        ellipsoidHeight = 0,
        track = 0.0,
        turnRate = 0.0,
        groundSpeed = 50.0,
        verticalRate = 0.0
    )


    // NL
    val nl1 = ownshipPos.copy(latitude = 52.75, longitude = 4.87)
    val nl2 = ownshipPos.copy(latitude = 52.89, longitude = 6.14)

    // GE
    val de1 = ownshipPos.copy(latitude = 51.84, longitude = 7.72)
    val de2 = ownshipPos.copy(latitude = 52.46, longitude = 8.957)

    // Canada
    val ca1 = ownshipPos.copy(latitude = 46.90, longitude = -73.80)
    val ca2 = ownshipPos.copy(latitude = 50.10, longitude = -70.44)

    @Test
    fun `No fleet should not call webservices`() = runBlocking {
        // Get mocked spatial service
        val spatialService: SpatialService = get(named("SpatialService"))
        val webServices = listOf(
            FakeWebService("ws1", 1000_000.0)
        )
        val dispatcher = ClusterDispatcher(
            spatialService = spatialService,
            webServices = webServices,
            fleetCheckInterval = 10, clusterInterval = 5_000, minWSRequestInterval = 200
        )

        // Setup mock to return fleet data
        coEvery { spatialService.scanFleet(any()) } returns listOf()

        // Collect emissions in a list
        val collectedResults = mutableListOf<FlowResult<List<AircraftPosition>>>()
        val collectionJob = launch {
            dispatcher.streamAircraft().collect { result ->
                collectedResults.add(result)
            }
        }

        delay(DELAY_BEFORE_COLLECTION)
        collectionJob.cancel()

        // Verify web services were called
        assertTrue(webServices.isNotEmpty())
        webServices.forEach { ws ->
            val fakeWs = ws as FakeWebService
            assertTrue(fakeWs.callCount == 0, "Web service ${fakeWs.getName()} was not called")
        }
    }

    @Test
    fun `single cluster uses all webservices`() = runBlocking {
        // Get mocked spatial service
        val spatialService: SpatialService = get(named("SpatialService"))
        val webServices = listOf(
            FakeWebService("ws1", 1000_000.0),
            FakeWebService("ws2", 1000_000.0),
            FakeWebService("ws3", 1000_000.0),
        )
        val dispatcher = ClusterDispatcher(
            spatialService = spatialService,
            webServices = webServices,
            fleetCheckInterval = 10, clusterInterval = 5_000, minWSRequestInterval = 200
        )

        coEvery { spatialService.scanFleet(any()) } returns listOf(nl1, nl2)

        // Collect emissions in a list
        val collectedResults = mutableListOf<FlowResult<List<AircraftPosition>>>()
        val collectionJob = launch {
            dispatcher.streamAircraft().collect { result ->
                collectedResults.add(result)
            }
        }

        delay(DELAY_BEFORE_COLLECTION)
        collectionJob.cancel()

        // Verify all web services were used
        webServices.forEach { ws ->
            val fakeWs = ws as FakeWebService
            assertTrue(fakeWs.callCount > 0, "Web service ${fakeWs.getName()} was not called")
        }
    }

    @Test
    fun `3 clusters will round robin all`() = runBlocking {
        // Get mocked spatial service
        val spatialService: SpatialService = get(named("SpatialService"))
        val webServices = listOf(
            FakeWebService("ws1", 250_000.0),
        )
        val dispatcher = ClusterDispatcher(
            spatialService = spatialService,
            webServices = webServices,
            fleetCheckInterval = 10_000,
            clusterInterval = 5_000,
            minWSRequestInterval = 200
        )
        // THis will create 3 cluster one a webservice with a radius of 250Km
        coEvery { spatialService.scanFleet(any()) } returns listOf(nl1, nl2, de1, de2, ca1, ca2)

        val collectedResults = mutableListOf<FlowResult<List<AircraftPosition>>>()
        val collectionJob = launch {
            dispatcher.streamAircraft().collect { result ->
                collectedResults.add(result)
            }
        }

        // Wait for a minimum of 3 calls
        delay(1000)
        collectionJob.cancel()

        // When the lastFetchedAt is still at 0, the cluster was never fetched
        dispatcher.getClusterStates().forEach { state ->
            assertTrue(state.lastFetchedAt != 0L, "Cluster ${state.cluster.center} never fetched")
        }
    }

    @Test
    fun `When a webservice fails, other should fullfill`() = runBlocking {
        // Get mocked spatial service
        val spatialService: SpatialService = get(named("SpatialService"))
        val webServices = listOf(
            FakeWebService("ws2", 250_000.0, timeout = true),
            FakeWebService("ws1", 250_000.0),
            FakeWebService("ws3", 250_000.0, fails = true),
        )
        val dispatcher = ClusterDispatcher(
            spatialService = spatialService,
            webServices = webServices,
            fleetCheckInterval = 10_000,
            clusterInterval = 5_000,
            minWSRequestInterval = 200
        )
        // THis will create 3 cluster one a webservice with a radius of 250Km
        coEvery { spatialService.scanFleet(any()) } returns listOf(nl1, nl2, de1, de2, ca1, ca2)

        val collectedResults = mutableListOf<FlowResult<List<AircraftPosition>>>()
        val collectionJob = launch {
            dispatcher.streamAircraft().collect { result ->
                collectedResults.add(result)
            }
        }

        // Wait for a minimum of 3 calls
        delay(2000)
        collectionJob.cancel()

        // Verify all clusters where fetched
        assertEquals(3, dispatcher.getClusterStates().size)
        dispatcher.getClusterStates().forEach { state ->
            assertTrue(state.lastFetchedAt != 0L, "Cluster ${state.cluster.center} never fetched")
        }

        // Verify all web services were used
        assertTrue(
            webServices.find { it.getName() == "ws1" }!!.callCount > 0,
            "Web service ${webServices[0].getName()} was not called"
        )
        assertTrue(
            webServices.find { it.getName() == "ws2" }!!.callCount == 0,
            "Web service ${webServices[0].getName()} was called"
        )
        assertTrue(
            webServices.find { it.getName() == "ws3" }!!.callCount == 0,
            "Web service ${webServices[0].getName()} was called"
        )

    }
}
