package nl.rvantwisk.gatas.webservice

import nl.rvantwisk.gatas.models.AircraftPosition

interface AircraftWebService {
    /**
     * Fetch all aircraft positions from the web service.
     */
    suspend fun fetchPositions(latitude: Double, longitude: Double, radiusM: Double): List<AircraftPosition>

    /**
     * Name of the webservice, used ofr logging or other display purpose
     */
    fun getName(): String
}