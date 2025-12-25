package nl.rvantwisk.gatas.server.metar

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.extensions.fuzzyEquals
import nl.rvantwisk.gatas.server.metar.model.Metar
import nl.rvantwisk.gatas.server.metar.model.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Starts the background service to periodically fetch and update METAR data.
 *
 * This function launches a long-running coroutine that continuously checks if it needs
 * to fetch new METAR data from the AviationWeather service. The fetching logic is
 * designed to be efficient and timely:
 *
 * 1.  **Activation Check**: The service is active only when there is at least one
 *     aircraft in the `fleet`. If the fleet is empty, it sleeps and checks again.
 * 2.  **Timing Logic**: A fetch is triggered under two conditions:
 *     - If it has been 15 minutes or more since the last successful fetch.
 *     - Or, if the current UTC minute of the hour is 10, 25, 40, or 55, which are
 *       times when METAR reports are typically updated.
 * 3.  **Fetching and Processing**: When a fetch is triggered, it calls [fetchMetars],
 *     filters out entries with invalid coordinates or missing pressure data, and
 *     stores the valid METARs using the `tile38` spatial service.
 * 4.  **Timeout and Error Handling**: TAny exceptions during the process
 *     (e.g., network issues, parsing errors) are caught, logged, and the service
 *     pauses for a short duration before retrying.
 *
 * The service runs indefinitely on an IO-optimized coroutine dispatcher until the
 * parent `scope` is cancelled.
 */
class MetarUpdateService : KoinComponent {
    private val log: Logger by inject { parametersOf(MetarUpdateService::class.simpleName!!) }
    private val httpClient: HttpClient by inject(named("aviationWeatherClient"));
    private val tile38: SpatialService by inject(named("SpatialService"))
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Ignore any fields or attributes in the XML we do not have in our DTO's
        @OptIn(ExperimentalXmlUtilApi::class)
        val xml = XML {
            defaultPolicy {
                unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
            }
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
    fun start() {
        var lastFetchInstant = Clock.System.now() - 60.minutes;
        scope.launch {
            while (isActive) {
                try {
                    // When there is no fleet flying, skip
                    if (tile38.scanFleet(1).isEmpty()) {
                        continue
                    }

                    // Fetch at least every 15 minutes or every 10/25/40 and 55 minute where we expect metar is updated
                    val now = Clock.System.now()
                    val minute = now.toLocalDateTime(TimeZone.UTC).minute
                    val minutesSinceLastFetch = (now - lastFetchInstant).inWholeMinutes
                    if (!(minutesSinceLastFetch >= 15 || minute in setOf(10, 25, 40, 55))) {
                        continue
                    }

                    withTimeout(30_000) {
                        val (processedMetars, duration) = measureTimedValue {
                            val metars = fetchMetars()
                                .filter {
                                    !it.latitude.fuzzyEquals(-99.9) &&
                                        !it.longitude.fuzzyEquals(-99.9) &&
                                        (it.altim_in_hg != null || it.sea_level_pressure_mb != null)
                                }
                            metars.forEach { metar ->
                                tile38.addMetar(metar)
                            }
                            metars.size
                        }

                        log.i { "Fetched and processed $processedMetars METARs in %.1fs".format(duration.inWholeMilliseconds / 1000.0) }
                        lastFetchInstant = Clock.System.now()
                    }
                } catch (e: Exception) {
                    log.e(e) { e.message ?: "" }
                    // Just add 10 seconds just in case we where rate limited
                }
                delay(15_000)
            }
        }
    }

    /**
     * Fetches METAR data from the AviationWeather service.
     *
     * This function makes an HTTP GET request to retrieve gzipped XML METAR data,
     * then parses the XML into a list of [Metar] objects.
     *
     * @return A list of [Metar] objects representing the fetched weather reports.
     * @throws [ClientRequestException] if the HTTP request fails.
     * @throws [SerializationException] if the XML parsing fails.
     * @throws [IOException] if there's an issue with reading or decompressing the data.
     */
    @OptIn(ExperimentalXmlUtilApi::class)
    suspend fun fetchMetars(): List<Metar> {
        val req = "https://aviationweather.gov/data/cache/metars.cache.xml.gz"
        val bytes = httpClient.get(req)
        val xmlBytes = withContext(Dispatchers.IO) {
            GZIPInputStream(bytes.readRawBytes().inputStream()).readBytes()
        }
        return xml.decodeFromString(Response.serializer(), xmlBytes.decodeToString()).data.METAR
    }
}
