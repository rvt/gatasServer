package nl.rvantwisk.gatas.extensions

import nl.rvantwisk.gatas.models.AircraftCategory

fun String.adsbToGatasCategory(): AircraftCategory = when (this) {
    "A0" -> AircraftCategory.POINT_OBSTACLE
    "A1" -> AircraftCategory.LIGHT
    "A2" -> AircraftCategory.SMALL
    "A3" -> AircraftCategory.LARGE
    "A4" -> AircraftCategory.HIGH_VORTEX
    "A5" -> AircraftCategory.HEAVY_ICAO
    "A6" -> AircraftCategory.AEROBATIC
    "A7" -> AircraftCategory.ROTORCRAFT
    "B1" -> AircraftCategory.GLIDER
    "B2" -> AircraftCategory.LIGHT_THAN_AIR
    "B3" -> AircraftCategory.SKY_DIVER
    // Some planes are really ultralight and are thus not a para glider.
    // Assuming that most, if not all paragliders will not use ADS-B we will use ULTRA_LIGHT_FIXED_WING
    //    "B4" -> AircraftCategory.PARA_GLIDER
    "B4" -> AircraftCategory.ULTRA_LIGHT_FIXED_WING
    "B6" -> AircraftCategory.UN_MANNED
    "B7" -> AircraftCategory.SPACE_VEHICLE
    "C1" -> AircraftCategory.SURFACE_EMERGENCY_VEHICLE
    "C2" -> AircraftCategory.SURFACE_VEHICLE
    "C3" -> AircraftCategory.POINT_OBSTACLE
    "C4" -> AircraftCategory.CLUSTER_OBSTACLE
    "C5" -> AircraftCategory.LINE_OBSTACLE
    else -> AircraftCategory.UNKNOWN
}
