package nl.rvantwisk.server

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.uber.h3core.H3Core
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import nl.rvantwisk.gatas.flows.AdsbGenericFlowService
import nl.rvantwisk.gatas.flows.AircraftFlowService
import nl.rvantwisk.gatas.flows.LocationService
import nl.rvantwisk.gatas.webservice.AdsbFiService
import nl.rvantwisk.gatas.webservice.AdsbLolService
import nl.rvantwisk.gatas.webservice.AircraftWebService
import nl.rvantwisk.gatas.webservice.AirplanesLiveService
import nl.rvantwisk.server.datastore.MetarService
import nl.rvantwisk.server.datastore.SpatialService
import nl.rvantwisk.server.datastore.tile38.setJsonOutput
import nl.rvantwisk.server.feeds.Tile38Updater
import nl.rvantwisk.server.flowservices.KtorClient
import nl.rvantwisk.server.flowservices.SimpleLocationService
import nl.rvantwisk.server.metar.MetarUpdateService
import nl.rvantwisk.server.udp.PerSenderRateLimiter
import nl.rvantwisk.server.udp.SimpleRateLimiter
import nl.rvantwisk.server.udp.TokenBucketRateLimiter
import nl.rvantwisk.server.udp.UdpAircraftService
import nl.rvantwisk.server.udp.UniqueIdRateLimiter
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureFrameworks() {
  val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  val tile38Host = System.getenv("TILE38_HOST") ?: "localhost"
  val tile38Port = System.getenv("TILE38_PORT")?.toInt() ?: 9851

  val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
  val redisPort = System.getenv("REDIS_PORT")?.toInt() ?: 6379

  install(Koin) {
//    slf4jLogger()
//        Tile38()
    modules(module {

      single<Logger>(named("Logger")) {
        Logger(config = StaticConfig(minSeverity = Severity.Info, logWriterList = listOf(TimestampLogWriter)))
      }
      // tagged logger factory
      factory { (tag: String) ->
        get<Logger>(named("Logger")).withTag(tag)
      }

      single<HttpClient>(named("adsbClient")) {
        KtorClient.getHttpClient(1500)
      }
      single<HttpClient>(named("aviationWeatherClient")) {
        KtorClient.getHttpXmlClient(10000)
      }

      single<LocationService> {
        SimpleLocationService()
      }

      single<MetarUpdateService> {
        MetarUpdateService()
      }
      single<H3Core> {
        H3Core.newInstance()
      }

      single<AircraftFlowService>(named("AdsbLolFlowService")) {
        AdsbGenericFlowService(get(named("AdsbLolService")), get<LocationService>())
      }
      single<AircraftFlowService>(named("AdsbFiFlowService")) {
        AdsbGenericFlowService(get(named("AdsbFiService")), get<LocationService>())
      }
      single<AircraftFlowService>(named("AirplanesLiveFlowService")) {
        AdsbGenericFlowService(get(named("AirplanesLiveService")), get<LocationService>())
      }

      single<String>(named("airplanesLiveKey")) {
//        runBlocking {
//          FileSystem.RESOURCES.source(".airplanesLiveKey".toPath()).use { fileSource ->
//            fileSource.buffer().use { bufferedFileSource ->
//              bufferedFileSource.readString(Charsets.US_ASCII).trim()
//            }
//          }
//        }
        System.getenv("AIRPLANESLIVEKEY") ?: "-"
      }

      single<AircraftWebService>(named("AdsbLolService")) {
        AdsbLolService()
      }
      single<AircraftWebService>(named("AdsbFiService")) {
        AdsbFiService()
      }
      single<AircraftWebService>(named("AirplanesLiveService")) {
        AirplanesLiveService()
      }

      single<SpatialService>(named("SpatialService")) {
        SpatialService()
      }
      single<MetarService>(named("MetarService")) {
        MetarService()
      }

      single<PerSenderRateLimiter>(named("TokenBucketRateLimiter")) {
        TokenBucketRateLimiter(5, Duration.parse("1000ms"))
      }

      single<UniqueIdRateLimiter>(named("UniqueIdRateLimiter")) {
        UniqueIdRateLimiter(5, Duration.parse("900ms"))
      }

      single<SimpleRateLimiter>(named("SimpleRateLimiter")) {
        SimpleRateLimiter(Duration.parse("900ms"))
      }



      // Need a read and write connection separatly
      // https://github.com/redis/lettuce/discussions/1896
      single<RedisClient>(named("RedisClientRead")) {
        val uri = RedisURI.Builder.redis(redisHost, redisPort).build()
        RedisClient.create(uri)
      }

      single<RedisClient>(named("RedisClientWrite")) {
        val uri = RedisURI.Builder.redis(redisHost, redisPort).build()
        RedisClient.create(uri)
      }

      single<StatefulRedisConnection<String, String>>(named("tile38ReadConnection")) {
        val uri = RedisURI.Builder.redis(tile38Host, tile38Port).build()
        val connection = RedisClient.create(uri).connect()
        runBlocking {
          connection.setJsonOutput()
        }
        connection
      }
      single<StatefulRedisConnection<String, String>>(named("tile38writeConnection")) {
        val uri = RedisURI.Builder.redis(tile38Host, tile38Port).build()
        val connection = RedisClient.create(uri).connect()
        runBlocking {
          connection.setJsonOutput()
        }
        connection
      }

      single<Tile38Updater> {
        Tile38Updater()
      }
      single<UdpAircraftService> {
        UdpAircraftService()
      }

    })
  }
}

object TimestampLogWriter : LogWriter() {
  override fun isLoggable(tag: String, severity: Severity): Boolean = true

  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    val timestamp = java.time.LocalDateTime.now()
    val formatted = "$timestamp [$severity] ($tag) $message"
    println(formatted)
    throwable?.printStackTrace()
  }
}
