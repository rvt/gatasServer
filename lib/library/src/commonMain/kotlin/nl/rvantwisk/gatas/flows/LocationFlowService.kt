package nl.rvantwisk.gatas.flows

import kotlinx.coroutines.flow.Flow


data class Position (val lat: Double, val lon: Double, val radius: Double)

interface LocationService {
    fun getLatest(): List<Position>
}