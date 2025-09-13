package nl.rvantwisk.gatas.flows

import kotlinx.coroutines.flow.Flow
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.RestResult

interface AircraftFlowService {
    fun streamAircraft(): Flow<RestResult<List<AircraftPosition>>>
}

