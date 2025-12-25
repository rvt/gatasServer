package nl.rvantwisk.gatas.lib.math


import nl.rvantwisk.gatas.lib.models.LatLon
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals

private const val EPS = 1e-6

class GeoMathTest {

    val p1 = LatLon(52.0, 5.0)
    val p2 = LatLon(52.1, 5.1)


    @Test
    fun `bearing north is zero degrees`() {
        val b = bearingFromInDeg(52.0, 5.0, 53.0, 5.0)
        assertEquals(0.0, b, 0.5)
    }

    @Test
    fun `bearing east is ninety degrees`() {
        val b = bearingFromInDeg(52.0, 5.0, 52.0, 6.0)
        assertEquals(90.0, b, 0.5)
    }

    @Test
    fun `bearing south is one eighty`() {
        val b = bearingFromInDeg(52.0, 5.0, 51.0, 5.0)
        assertEquals(180.0, b, 0.5)
    }

    @Test
    fun `bearing west is two seventy`() {
        val b = bearingFromInDeg(52.0, 5.0, 52.0, 4.0)
        assertEquals(270.0, b, 0.5)
    }

    @Test
    fun `toBearing normalizes double`() {
        assertEquals(10.0, toBearing(370.0))
        assertEquals(350.0, toBearing(-10.0))
    }

    @Test
    fun `toBearing normalizes int`() {
        assertEquals(10, toBearing(370))
        assertEquals(350, toBearing(-10))
    }

    @Test
    fun `northEastDistance north only`() {
        val ne = northEastDistance(52.0, 5.0, 52.1, 5.0)
        assertEquals(11113.9, ne.north, 0.1)
        assertEquals(0.0, ne.east, 0.1)
    }

    @Test
    fun `northEastDistance east only`() {
        val ne = northEastDistance(52.0, 5.0, 52.0, 5.1)
        assertEquals(6853.6, ne.east, 0.1)
        assertEquals(0.0, ne.north, 0.1)
    }

    @Test
    fun `northEastDistance north east`() {
        val ne = northEastDistance(52.0, 5.0, 51.9, 4.9)
        assertEquals(-6853.6, ne.east, 0.1)
        assertEquals(-11113.9, ne.north, 0.1)
    }

    @Test
    fun `distanceFast matches hypot of north east`() {
        val ne = northEastDistance(p1.lat, p1.lon, p2.lat, p2.lon)
        val expected = sqrt(ne.north * ne.north + ne.east * ne.east)
        val actual = distanceFast(p1.lat, p1.lon, p2.lat, p2.lon)

        assertEquals(expected, actual, EPS)

        // Extra validation against 'official' haversine
        val distance = p1.haversineDistance(p2)
        assertEquals(expected, distance, 10.0)
    }

    @Test
    fun `getDistanceRelNorthRelEastDouble consistent`() {
        val d = getDistanceRelNorthRelEastDouble(52.0, 5.0, 52.01, 5.02)

        val expected = sqrt(
            d.relNorthMeter * d.relNorthMeter +
                d.relEastMeter * d.relEastMeter
        )

        assertEquals(expected, d.distance, EPS)
        assertEquals(50.9, d.bearing, 0.1)
    }

    @Test
    fun `getDistanceRelNorthRelEastInt rounds correctly`() {
        val d = getDistanceRelNorthRelEastInt(52.0, 5.0, 52.01, 5.02)

        assertEquals(1765, d.distance)
        assertEquals(1111, d.relNorth)
        assertEquals(1371, d.relEast)
        assertEquals(51, d.bearing)
    }


    @Test
    fun `haversineDistance Test`() {
        val distance = p1.haversineDistance(p2)

        assertEquals(13053.8, distance, 0.1)
    }
}
