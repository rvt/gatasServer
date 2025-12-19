package nl.rvantwisk.gatas.lib.flows


data class Position (val lat: Double, val lon: Double, val radius: Double)

interface LocationService {
    fun getLatest(): List<Position>
}
