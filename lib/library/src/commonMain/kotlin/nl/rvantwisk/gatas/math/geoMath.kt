package nl.rvantwisk.gatas.math

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

const val DEG_TO_RAD = PI.toDouble() / 180f
const val RAD_TO_DEG = 180f / PI.toDouble()
const val TWO_PI = (2 * PI).toDouble()

data class DistanceRelNorthRelEastInt(
    val distance: UInt,
    val relNorth: Int,
    val relEast: Int,
    val bearing: Int
)

data class RelNorthRelEast(
    val north: Double,
    val east: Double
)

fun bearingFromInRad(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val fromLatRad = fromLat * DEG_TO_RAD
    val fromLonRad = fromLon * DEG_TO_RAD
    val toLatRad = toLat * DEG_TO_RAD
    val toLonRad = toLon * DEG_TO_RAD

    val dLon = toLonRad - fromLonRad
    val cosToLat = cos(toLatRad)

    val y = sin(dLon) * cosToLat
    val x = cos(fromLatRad) * sin(toLatRad) -
            sin(fromLatRad) * cosToLat * cos(dLon)

    val bearingRad = atan2(y, x)
    return (bearingRad + TWO_PI) % TWO_PI
}

fun bearingFromInDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    return bearingFromInRad(fromLat, fromLon, toLat, toLon) * RAD_TO_DEG
}


fun toBearing(angle: Double): Double {
    var a = angle
    while (a < 0f) a += 360f
    while (a >= 360f) a -= 360f
    return a
}

fun toBearing(angle: Int): Int {
    var a = angle
    while (a < 0) a += 360
    while (a >= 360) a -= 360
    return a
}

data class DistanceRelNorthRelEastDouble(
    val distance: Double,
    val relNorthMeter: Double,
    val relEastMeter: Double,
    val bearing: Double
)

fun getDistanceRelNorthRelEastDouble(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): DistanceRelNorthRelEastDouble {
    val bearing = bearingFromInRad(fromLat, fromLon, toLat, toLon) * RAD_TO_DEG
    val ne = northEastDistance(fromLat, fromLon, toLat, toLon)
    val distance = hypot(ne.north, ne.east)
    return DistanceRelNorthRelEastDouble(distance, ne.north, ne.east, bearing)
}

fun getDistanceRelNorthRelEastInt(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): DistanceRelNorthRelEastInt {
    val drne = getDistanceRelNorthRelEastDouble(fromLat, fromLon, toLat, toLon)

    val bearing = toBearing((drne.bearing + 0.5f).toInt())
    return DistanceRelNorthRelEastInt(
        distance = (drne.distance + 0.5f).toUInt(),
        relNorth = (drne.relNorthMeter + 0.5f).toInt(),
        relEast = (drne.relEastMeter + 0.5f).toInt(),
        bearing = bearing
    )
}

fun northEastDistance(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double
): RelNorthRelEast {
    val kx = cos(fromLat * DEG_TO_RAD) * 111_321.0
    val dx = (toLon - fromLon) * kx
    val dy = (toLat - fromLat) * 111_139.0
    return RelNorthRelEast(north = dy, east = dx)
}

fun distanceFast(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val ne = northEastDistance(fromLat, fromLon, toLat, toLon)
    return hypot(ne.north, ne.east) // uses sqrt(n^2 + e^2)
}


data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

fun getBoundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
    val degToRad = PI / 180.0
    val latRad = lat * degToRad

    val deltaLat = radiusMeters / 111_139.0
    val deltaLon = radiusMeters / (cos(latRad) * 111_321.0)

    return BoundingBox(
        minLat = lat - deltaLat,
        maxLat = lat + deltaLat,
        minLon = lon - deltaLon,
        maxLon = lon + deltaLon
    )
}
