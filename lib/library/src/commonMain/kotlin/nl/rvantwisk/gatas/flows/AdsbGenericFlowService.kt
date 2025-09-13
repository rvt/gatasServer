package nl.rvantwisk.gatas.flows

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.RestResult
import nl.rvantwisk.gatas.models.RestStatus
import nl.rvantwisk.gatas.webservice.AircraftWebService
import org.koin.core.component.KoinComponent

private val log =
    Logger.withTag(AdsbGenericFlowService::class.simpleName ?: "AdsbGenericFlowService")

class AdsbGenericFlowService(
    val aircraftWebService: AircraftWebService,
    val locationService: LocationService
) : AircraftFlowService, KoinComponent {
    override fun streamAircraft(): Flow<RestResult<List<AircraftPosition>>> {
        return flow {
            while (true) {
                try {
                    val pos = locationService.getLatest()
                    // TODO: take the center of the using geok
                    // https://github.com/piruin/geok
                    if (pos.isNotEmpty()) {
                        val positions = aircraftWebService.fetchPositions(
                            pos.first().lat,
                            pos.first().lon,
                            if (pos.first().radius == 0.0) 200000.0 else pos.first().radius
                        )
                        emit(
                            RestResult(
                                source = aircraftWebService.getName(),
                                data = positions,
                                status = RestStatus.SUCCESS
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.d { "Failed to call rest service ${aircraftWebService.getName()} ${e.message}" }
// Emissions from 'catch' blocks are prohibited in order to avoid unspecified behaviour, 'Flow.catch' operator can be used instead.
//                    emit(
//                        RestResult(
//                            source = aircraftWebService.getName(),
//                            data = emptyList(),
//                            status = RestStatus.FAILURE,
//                        )
//                    )
                }
                delay(1000)
            }
        }
    }
}


