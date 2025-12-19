package nl.rvantwisk.gatas.server.flowservices

import kotlinx.coroutines.runBlocking
import me.piruin.geok.LatLng
import me.piruin.geok.geometry.Polygon
import nl.rvantwisk.gatas.lib.flows.LocationService
import nl.rvantwisk.gatas.lib.flows.Position
import nl.rvantwisk.gatas.lib.math.getDistanceRelNorthRelEastDouble
import nl.rvantwisk.gatas.server.STORE_MAX_RADIUS
import nl.rvantwisk.gatas.server.datastore.SpatialService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class SimpleLocationService : LocationService, KoinComponent {

  private val spatialService: SpatialService by inject(named("SpatialService"))

  override fun getLatest(): List<Position> {

    return runBlocking {
      val fleet = spatialService.scanFleet(100)

      if (fleet.isNotEmpty()) {
        // Convert to LatLng
        val coords = fleet.map { LatLng(it.latitude, it.longitude) }.toMutableList()

        // Ensure at least 3 coordinates by cloning and slightly offsetting the last one
        while (coords.size < 3) {
          coords += coords.last().let { LatLng(it.latitude + 1e-6, it.longitude + 1e-6) }
        }

        // Keep only points within Europe-ish region (~2500 km radius around central Europe)
        val centerEurope = LatLng(50.80, 10.30)
        val maxRadiusMeters = 2_500_000.0
        val europeRegion = coords.filter { point ->
          getDistanceRelNorthRelEastDouble(
            centerEurope.latitude, centerEurope.longitude,
            point.latitude, point.longitude
          ).distance <= maxRadiusMeters
        }

        // If nothing left, bail out early
        if (europeRegion.isEmpty()) return@runBlocking emptyList<Position>()

        // Compute centroid from Europe-only points
        val centroid = Polygon(europeRegion).centroid

        // Compute farthest distance from centroid (and add store radius)
        val farthest = coords.maxOfOrNull {
          getDistanceRelNorthRelEastDouble(
            centroid.latitude, centroid.longitude,
            it.latitude, it.longitude
          ).distance + STORE_MAX_RADIUS
        }?.coerceIn(100_000.0, maxRadiusMeters)

        return@runBlocking listOf(
          Position(centroid.latitude, centroid.longitude, farthest ?: 100_000.0)
        )
      } else {
        return@runBlocking emptyList()
      }
    }

  }
}
