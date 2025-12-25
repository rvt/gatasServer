package nl.rvantwisk.gatas.server

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.uber.h3core.H3Core
import io.ktor.client.*
import io.ktor.server.application.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import nl.rvantwisk.gatas.lib.services.AltitudeService
import nl.rvantwisk.gatas.lib.services.egm2008.EGM2008
import nl.rvantwisk.gatas.lib.services.egm2008.EarthGravitationalModel
import nl.rvantwisk.gatas.lib.utils.readByteArray
import nl.rvantwisk.gatas.lib.webservice.AdsbFiService
import nl.rvantwisk.gatas.lib.webservice.AircraftWebService
import nl.rvantwisk.gatas.lib.webservice.AirplanesLiveService
import nl.rvantwisk.gatas.server.datastore.MetarCacheFactoryService
import nl.rvantwisk.gatas.server.datastore.SpatialService
import nl.rvantwisk.gatas.server.datastore.tile38.setJsonOutput
import nl.rvantwisk.gatas.server.feeds.AircraftFlowToTile38
import nl.rvantwisk.gatas.server.flowservices.ClusterDispatcher
import nl.rvantwisk.gatas.server.flowservices.KtorClient
import nl.rvantwisk.gatas.server.metar.MetarUpdateService
import nl.rvantwisk.gatas.server.udp.SimpleRateLimiter
import nl.rvantwisk.gatas.server.udp.UdpAircraftService
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureFrameworks() {
    val tile38Host = System.getenv("TILE38_HOST") ?: "localhost"
    val tile38Port = System.getenv("TILE38_PORT")?.toInt() ?: 9851

//  val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
//  val redisPort = System.getenv("REDIS_PORT")?.toInt() ?: 6379

    install(Koin) {

        modules(module {

            single<Logger>(named("Logger")) {
                Logger(
                    config = StaticConfig(
                        minSeverity = Severity.Info,
                        logWriterList = listOf(TimestampLogWriter)
                    )
                )
            }
            // tagged logger factory
            factory { (tag: String) -> get<Logger>(named("Logger")).withTag(tag) }

            single<HttpClient>(named("adsbClient")) { KtorClient.getHttpClient(1500) }

            single<HttpClient>(named("aviationWeatherClient")) { KtorClient.getHttpXmlClient(10000) }

//            single<LocationFlowService> { SimpleLocationService() }

            single<ClusterDispatcher> {
                ClusterDispatcher(
                    webServices = get(named("asyncWebServices")),
                    spatialService = get(named("SpatialService"))
                )
            }

            single<MetarUpdateService> { MetarUpdateService() }

            single<H3Core> { H3Core.newInstance() }

//            single<AircraftFlowService>(named("AdsbFiFlowService")) {
//                AdsbGenericFlowService(get(named("AdsbFiService")), get<LocationFlowService>())
//            }
//            single<AircraftFlowService>(named("AirplanesLiveFlowService")) {
//                AdsbGenericFlowService(get(named("AirplanesLiveService")), get<LocationFlowService>())
//            }

            single<AircraftWebService>(named("AdsbFiService")) { AdsbFiService() }

            single<AircraftWebService>(named("AirplanesLiveService")) { AirplanesLiveService() }

            // Create the list manually if you need specific ordering or filtering
            single<List<AircraftWebService>>(named("asyncWebServices")) {
                listOf(
                    get(named("AdsbFiService")),
                    get(named("AirplanesLiveService"))
                )
            }


            single<String>(named("airplanesLiveKey")) {
                val key = System.getenv("AIRPLANESLIVEKEY") ?: "-"
                key
            }

            single<SpatialService>(named("SpatialService")) { SpatialService() }

            single<MetarCacheFactoryService>(named("MetarService")) { MetarCacheFactoryService() }

            single<SimpleRateLimiter>(named("SimpleRateLimiter")) { SimpleRateLimiter(Duration.parse("900ms"), 10, 1) }

            // Need a read and write connection separatly
            // https://github.com/redis/lettuce/discussions/1896
//      single<RedisClient>(named("RedisClientRead")) {
//        val uri = RedisURI.Builder.redis(redisHost, redisPort).build()
//        RedisClient.create(uri)
//      }
//
//      single<RedisClient>(named("RedisClientWrite")) {
//        val uri = RedisURI.Builder.redis(redisHost, redisPort).build()
//        RedisClient.create(uri)
//      }

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

            single<AircraftFlowToTile38> { AircraftFlowToTile38() }

            single<UdpAircraftService> { UdpAircraftService() }

            single<AltitudeService> { AltitudeService() }

            single<EarthGravitationalModel>() {
                val egm = EGM2008()
                egm.init(readByteArray("./egm2008.bin".toPath()))
                egm
            }
        })
    }
}

/**
 * Add's a timestamp to each log line, you gotta wonder why this was even needed, but here we are!
 */
object TimestampLogWriter : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean = true

    @OptIn(ExperimentalTime::class)
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val timestamp = Clock.System.now()
        val formatted = "$timestamp [$severity] ($tag) $message"
        println(formatted)
        throwable?.printStackTrace()
    }
}
