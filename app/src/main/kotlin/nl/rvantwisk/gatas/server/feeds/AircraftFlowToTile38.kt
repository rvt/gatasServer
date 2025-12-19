package nl.rvantwisk.gatas.server.feeds

import co.touchlab.kermit.Logger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import nl.rvantwisk.gatas.lib.flows.AircraftFlowService
import nl.rvantwisk.gatas.lib.models.FlowStatus
import nl.rvantwisk.gatas.server.datastore.SpatialService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

/**
 * A service class responsible for connecting a live stream of aircraft data to the spatial database.
 *
 * This class injects an [AircraftFlowService] to receive real-time aircraft updates and a
 * [SpatialService] to process and store that data. It launches a long-running coroutine
 * that listens to the aircraft data flow, filters for successful updates, and sends them
 * to the spatial service.
 *
 * The internal coroutine scope is configured with a [SupervisorJob] to ensure that an
 * exception during the processing of a single aircraft update does not terminate the
 * entire data stream.
 *
 * @property start Starts the background process of listening for and processing aircraft data.
 * @property stop Cancels the coroutine scope, stopping all data processing initiated by this class.
 */
class AircraftFlowToTile38 : KoinComponent {
    private val log: Logger by inject { parametersOf(AircraftFlowToTile38::class.simpleName!!) }

    private val aircraftFlowService: AircraftFlowService by inject(named("AirplanesLiveFlowService"))

    private val spatialService: SpatialService by inject(named("SpatialService"))

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun start() {
        scope.launch {
            aircraftFlowService.streamAircraft()
                .filter { it.status == FlowStatus.SUCCESS && it.data != null }
                .collect { aircraftData ->
                    try {
                        spatialService.sendAircrafts(aircraftData.data!!)
                    } catch (e: Exception) {
                        log.e(e) { "Failed to send aircraft data: ${e.message}" }
                    }
                }
        }
    }

    fun stop() {
        scope.cancel()
    }
}


