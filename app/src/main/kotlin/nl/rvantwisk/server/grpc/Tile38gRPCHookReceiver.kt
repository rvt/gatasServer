package nl.rvantwisk.server.grpc

import co.touchlab.kermit.Logger
import com.tile38.hservice.HookServiceGrpcKt
import com.tile38.hservice.MessageReply
import com.tile38.hservice.MessageRequest
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import nl.rvantwisk.server.datastore.tile38.models.Tile38GrpcObject
import nl.rvantwisk.server.flowservices.KtorClient.getKoin
import nl.rvantwisk.server.server.grpc.chunkedTimed
import org.koin.core.component.KoinComponent
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private val log =  getKoin().get<Logger>().withTag(Tile38gRPCHookReceiver::class.simpleName!!)

class Tile38gRPCHookReceiver(private val port: Int) : KoinComponent {

    @OptIn(ExperimentalSerializationApi::class)
    val incomingGeoEvents = MutableSharedFlow<Tile38GrpcObject>(extraBufferCapacity = 500)
    @OptIn(ExperimentalSerializationApi::class)
    val watcherFlows = ConcurrentHashMap<String, MutableSharedFlow<Tile38GrpcObject>>()
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalSerializationApi::class)
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(Receiver(incomingGeoEvents))
        .build()

    fun start() {
        server.start()
        println("Server running on port $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.i { "**** Shutting down gRPC server since JVM is shutting down" }
                server.shutdown()
                log.i { "**** Server shut down" }
            }
        )
        startDispatching();
    }

    class Receiver @OptIn(ExperimentalSerializationApi::class)
    constructor(val incomingGeoEvents: MutableSharedFlow<Tile38GrpcObject>) :
        HookServiceGrpcKt.HookServiceCoroutineImplBase() {
        val json = Json {
            ignoreUnknownKeys = true
        }

        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun send(request: MessageRequest): MessageReply {
            try {
                val decoded = json.decodeFromString(Tile38GrpcObject.serializer(), request.value)
                incomingGeoEvents.emit(decoded) // suspends if necessary
            } catch (e: Exception) {
                log.w { "Failed to decode incoming hook: ${e.message}" }
            }

            return MessageReply.newBuilder().setOk(true).build()
        }
    }


    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    // You could deduplicate on targetId:
    @OptIn(ExperimentalSerializationApi::class)
    fun deduplicate2(batch: List<Tile38GrpcObject>) = batch.distinctBy { it.id }

    // You could also keep the most recent by converting to a Map:
    @OptIn(ExperimentalSerializationApi::class)
    fun deduplicate(batch: List<Tile38GrpcObject>): List<Tile38GrpcObject> {
        return batch
            .groupBy { it.id }
            .mapValues { (_, updates) -> updates.maxByOrNull { it.time }!! }
            .values
            .toList()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun startDispatching() {
        log.i("startDispatching")
        scope.launch {
            incomingGeoEvents.collect { update ->
                val flow = watcherFlows.getOrPut(update.hook) {
                    MutableSharedFlow<Tile38GrpcObject>(extraBufferCapacity = 100)
                        .also { watcherFlow ->
                            startWatcherBatcher(update.hook, watcherFlow)
                        }
                }
                flow.tryEmit(update)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun startWatcherBatcher(watcherId: String, flow: SharedFlow<Tile38GrpcObject>) {
        print("Starting watcher $watcherId")
        scope.launch {
            flow
                .buffer(capacity = 100)  // optional: local buffer
                .chunkedTimed(timeout = 200.milliseconds, maxSize = 50)
                .collect { batch ->
                    val deduped = deduplicate(batch)
                    log.i("Received $watcherId batch of ${deduped.size} updates")

//                    sendBatchToAircraft(watcherId, deduped)
                }
        }
    }
}
