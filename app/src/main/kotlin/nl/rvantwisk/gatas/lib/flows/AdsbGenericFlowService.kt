package nl.rvantwisk.gatas.lib.flows

import co.touchlab.kermit.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.models.FlowResult
import nl.rvantwisk.gatas.lib.models.FlowStatus
import nl.rvantwisk.gatas.lib.webservice.AircraftWebService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class AdsbGenericFlowService(
    private val aircraftWebService: AircraftWebService,
    private val locationService: LocationService
) : AircraftFlowService, KoinComponent {

    private val log: Logger by inject { parametersOf(AdsbGenericFlowService::class.simpleName!!) }

    override fun streamAircraft(): Flow<FlowResult<List<AircraftPosition>>> = flow {
        while (true) {
            val result = try {
                withTimeout(1500) {
                    val pos = locationService.getLatest()
                    if (pos.isEmpty()) return@withTimeout null

                    log.i { "Fetching aircraft from ${aircraftWebService.getName()}" }
                    val first = pos.first()
                    val radius = if (first.radius == 0.0) 200_000.0 else first.radius

                    val positions = aircraftWebService.fetchPositions(first.lat, first.lon, radius)
                    FlowResult(
                        source = aircraftWebService.getName(),
                        data = positions,
                        status = FlowStatus.SUCCESS
                    )
                }
            } catch (e: TimeoutCancellationException) {
                log.w { "REST call to ${aircraftWebService.getName()} timed out" }
                null
            } catch (e: Exception) {
                log.e(e) { "Error fetching from ${aircraftWebService.getName()}" }
                null
            }

            result?.let { emit(it) }
            delay(1250)
        }
    }
}
