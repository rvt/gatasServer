package nl.rvantwisk.gatas.lib.extensions

import nl.rvantwisk.gatas.lib.math.distanceFast
import nl.rvantwisk.gatas.lib.models.AircraftPosition

/**
 * Filter out aircraft that are on the ground and we are further away than 5Km.
 * This will get traffic in the air higher priority, however when you are close to the airport
 * ground aircraft get's priority
 */
fun List<AircraftPosition>.filterByDistanceOnGround(
  ownshipLat: Double, ownshipLon: Double,
  groundDistanceMeters: Int,
  maxDistancemeters: Int
): List<AircraftPosition> {

  return this.filter { position ->
    val rel = distanceFast(
      ownshipLat,
      ownshipLon,
      position.latitude,
      position.longitude
    )

    if (position.isGround) {
      // On the ground: keep only if close enough
      rel < groundDistanceMeters
    } else {
      // Airborne: keep if within maxDistancemeters
      rel < maxDistancemeters
    }
  }
}
