package nl.rvantwisk.gatas.lib.flows

import kotlinx.coroutines.flow.Flow
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.models.FlowResult

interface AircraftFlowService {
    fun streamAircraft(): Flow<FlowResult<List<AircraftPosition>>>
}

