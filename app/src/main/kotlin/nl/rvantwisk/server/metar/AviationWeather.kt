package nl.rvantwisk.server.metar

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.rvantwisk.server.datastore.SpatialService
import nl.rvantwisk.server.extensions.equalsWithTolerance
import nl.rvantwisk.server.metar.model.Metar
import nl.rvantwisk.server.metar.model.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.util.zip.GZIPInputStream

class MetarUpdateService : KoinComponent {
  private val log: Logger by inject { parametersOf(MetarUpdateService::class.simpleName!!) }

  private val httpClient: HttpClient by inject(named("aviationWeatherClient"));
  private val tile38: SpatialService by inject(named("SpatialService"))
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  companion object {
    @OptIn(ExperimentalXmlUtilApi::class)
    val xml = XML {
      defaultPolicy {
        // Ignore any fields or atributes in the XML we do not have in our DTO's
        unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
      }
    }

  }


  @OptIn(ExperimentalLettuceCoroutinesApi::class)
  fun start() {
    scope.launch {

      var lastFetch: Long = 0 // epoch millis of last METAR fetch

      while (isActive) {


        if (tile38.scanFleet(1).isNotEmpty()) {
          val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
          val epochMillis = System.currentTimeMillis()
          val minute = now.minute
          val minutesSinceLastFetch = (epochMillis - lastFetch) / 60_000

          val shouldFetch = when {
            // Fleet became active and last fetch was too long ago
            minutesSinceLastFetch >= 15 -> true
            // Otherwise, only trigger on exact slots (10, 25, 40, 55)
            minute in listOf(10, 25, 40, 55) -> true
            else -> false
          }

          if (shouldFetch) {
            log.i { "Start: Fetching METARs" }
            fetchMetars()
              .asSequence()
              .filter {
                it.latitude?.equalsWithTolerance(-99.9) == false &&
                  it.longitude?.equalsWithTolerance(-99.9) == false &&
                  (it.altim_in_hg != null || it.sea_level_pressure_mb != null)
              }
              .forEach { metar ->
                tile38.addMetar(metar)
              }
            log.i { "Done: Fetching METARs" }
            lastFetch = epochMillis
          }
        }

        // Calculate exact sleep until next minute boundary
        val millisToNextMinute = 60_000 - (System.currentTimeMillis() % 60_000)
        delay(millisToNextMinute + 1000)
      }
    }
  }

  @OptIn(ExperimentalXmlUtilApi::class)
  suspend fun fetchMetars(): List<Metar> {
    val req = "https://aviationweather.gov/data/cache/metars.cache.xml.gz"
    val bytes = httpClient.get(req)
    val xmlBytes = GZIPInputStream(bytes.readRawBytes().inputStream()).readBytes()
    return xml.decodeFromString(Response.serializer(), xmlBytes.decodeToString()).data.METAR
  }
}
