package nl.rvantwisk.server.feeds

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.rvantwisk.gatas.flows.AircraftFlowService
import nl.rvantwisk.gatas.models.FlowStatus
import nl.rvantwisk.server.datastore.SpatialService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class Tile38Updater : KoinComponent {

  private val aircraftFlowService: AircraftFlowService by inject(named("AirplanesLiveFlowService"))

  private val spatialService: SpatialService by inject(named("SpatialService"))

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  @OptIn(ExperimentalLettuceCoroutinesApi::class)
  fun start() {
    scope.launch {
      aircraftFlowService.streamAircraft()
        .filter { it.status == FlowStatus.SUCCESS && it.data != null }
        .map { it.data!! }
        .collect {
          spatialService.sendAircrafts(it)
        }
    }
  }

  fun stop() {
    scope.cancel()
  }
}


