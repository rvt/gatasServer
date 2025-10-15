package nl.rvantwisk.gatas.flows

import kotlinx.coroutines.flow.Flow
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.FlowResult

interface AircraftFlowService {
    fun streamAircraft(): Flow<FlowResult<List<AircraftPosition>>>
}

