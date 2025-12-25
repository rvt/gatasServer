package nl.rvantwisk.gatas.lib.utils

import nl.rvantwisk.gatas.lib.extensions.NM_TO_METERS
import nl.rvantwisk.gatas.lib.math.haversineDistance
import nl.rvantwisk.gatas.lib.math.sortedByDistanceFrom
import nl.rvantwisk.gatas.lib.models.LatLon
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class FixedRadiusClustererTest {


    // https://www.mapdevelopers.com/draw-circle-tool.php?circles=%5B%5B20534.91%2C52.7509707%2C4.878299%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B20535.18%2C51.8438484%2C7.7236662%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B40588.75%2C46.9045055%2C-73.9202988%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B40587.55%2C48.9073474%2C-72.3822129%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B14029.09%2C52.8988519%2C6.1428761%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B14033.44%2C52.4592451%2C8.9553761%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B161860.23%2C52.445894%2C6.8682419%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%2C%5B244828.89%2C47.9136912%2C-73.0984227%2C%22%23AAAAAA%22%2C%22%23000000%22%2C0.4%5D%5D

    @Test
    fun `clusters Europe and Canada separately`() {
        val points = listOf(
            // Europe (NL + DE)
            LatLon(52.7, 4.8),
            LatLon(52.89, 6.14),
            LatLon(51.84, 7.72),
            LatLon(52.46, 8.95),

            // Canada
            LatLon(46.90, -75.80),
            LatLon(48.90, -72.38)
        )

        val clusterer = FixedRadiusClusterer(points, radiusSizes=listOf(250_000.0 * NM_TO_METERS))
        print(clusterer.toGraphviz())

        Assertions.assertEquals(2, clusterer.clusters.size)

        // Verify each cluster internally fits radius constraint
        clusterer.clusters.forEach { cluster ->
            cluster.points.forEach { p ->
                val d = p.haversineDistance(cluster.center)
                assertTrue(
                    d <= 250_000.0,
                    "Point $p is $d meters from center, exceeds maxRadius"
                )
            }
        }
    }

    @Test
    fun `all points in one cluster when close together`() {
        val points = listOf(
            LatLon(52.0, 5.0),
            LatLon(52.05, 5.05),
            LatLon(52.1, 5.1)
        )

        val clusterer = FixedRadiusClusterer(points, radiusSizes=listOf(100_000.0 * NM_TO_METERS))
        print(clusterer.toGraphviz())

        Assertions.assertEquals(1, clusterer.clusters.size)
        Assertions.assertEquals(3, clusterer.clusters[0].points.size)
    }

    @Test
    fun `each point becomes its own cluster when too far apart`() {
        val points = listOf(
            LatLon(52.0, 5.0),
            LatLon(40.0, -74.0), // NYC
            LatLon(35.0, 139.0)  // Tokyo
        )

        val clusterer = FixedRadiusClusterer(points, radiusSizes=listOf(100_000.0 * NM_TO_METERS))
        print(clusterer.toGraphviz())

        Assertions.assertEquals(3, clusterer.clusters.size)
        clusterer.clusters.forEach {
            Assertions.assertEquals(1, it.points.size)
        }
    }

    @Test
    fun `cluster center is centroid of points`() {
        val points = listOf(
            LatLon(50.0, 5.0),
            LatLon(52.0, 7.0)
        )

        val clusterer = FixedRadiusClusterer(points, radiusSizes=listOf(500_000.0 * NM_TO_METERS))
        print(clusterer.toGraphviz())
        val cluster = clusterer.clusters.single()

        assertEquals(51.0, cluster.center.lat, 1e-6)
        assertEquals(6.0, cluster.center.lon, 1e-6)
    }

    @Test
    fun `empty input yields no clusters`() {
        val clusterer = FixedRadiusClusterer(emptyList(), radiusSizes=listOf(250_000.0 * NM_TO_METERS))
        assertTrue(clusterer.clusters.isEmpty())
    }


    @Test
    fun `Two Points, Europe Canada`() {
        val l = mutableListOf<LatLon>()
        // NL
        l.add(LatLon(lat = 52.75, lon = 4.87))
        l.add(LatLon(lat = 52.89, lon = 6.14))

        // GE
        l.add(LatLon(lat = 51.84, lon = 7.72))
        l.add(LatLon(lat = 52.46, lon = 8.95))

        // Canada
        l.add(LatLon(lat = 46.90, lon = -73.80))
        l.add(LatLon(lat = 48.90, lon = -72.38))


        val tree = FixedRadiusClusterer(l, radiusSizes=listOf(250_000.0 * NM_TO_METERS))
        print(tree.toGraphviz())

        val clusterPoints = tree.centers().sortedByDistanceFrom(LatLon(52.8, 5.5))
        assertEquals(2, clusterPoints.size)
        assertTrue(LatLon(52.46, 6.92).haversineDistance(clusterPoints[0]) < 2800)
        assertTrue(LatLon(47.9, -73.09).haversineDistance(clusterPoints[1]) < 2500)
    }

    @Test
    fun `Two Points, Europe Canada, cluster size given`() {
        val l = mutableListOf<LatLon>()
        // NL
        l.add(LatLon(lat = 52.75, lon = 4.87))
        l.add(LatLon(lat = 52.89, lon = 6.14))

        // GE
        l.add(LatLon(lat = 51.84, lon = 7.72))
        l.add(LatLon(lat = 52.46, lon = 8.95))

        // Canada
        l.add(LatLon(lat = 46.90, lon = -73.80))
        l.add(LatLon(lat = 50.10, lon = -70.44))

        val tree = FixedRadiusClusterer(l, radiusSizes=listOf(350000.0, 100000.0))

        val clusterPoints = tree.clusters.sortedByDistanceFrom(LatLon(52.8, 5.5))
        print(tree.toGraphviz())
        assertEquals(3, clusterPoints.size)

        assertTrue(LatLon(52.46, 6.92).haversineDistance(clusterPoints[0].center) < 2800)
        assertEquals(141492.0, tree.clusters[0].effectiveRadius, 1.0)

        assertTrue(LatLon(50.10, -70.44).haversineDistance(clusterPoints[1].center) < 2500)
        assertEquals(0.0, tree.clusters[2].effectiveRadius)

        assertTrue(LatLon(46.90, -73.80).haversineDistance(clusterPoints[2].center) < 2500)
        assertEquals(0.0, tree.clusters[2].effectiveRadius)

    }

    @Test
    fun `Two points Europe Germany`() {
        val l = mutableListOf<LatLon>()
        // NL
        l.add(LatLon(lat = 52.75, lon = 4.87))
        l.add(LatLon(lat = 52.89, lon = 6.14))

        // DU
        l.add(LatLon(lat = 51.84, lon = 7.72))
        l.add(LatLon(lat = 52.46, lon = 8.95))

        val tree = FixedRadiusClusterer(l, radiusSizes=listOf(150_000.0 ))

        val clusterPoints = tree.centers().sortedByDistanceFrom(LatLon(52.8, 5.5))
        print(tree.toGraphviz())

        assertEquals(2, clusterPoints.size)
        assertTrue(LatLon(52.8, 5.51).haversineDistance(clusterPoints[0]) < 2500)
        assertEquals(43408.0, tree.clusters[0].effectiveRadius, 1.0)
        assertTrue(LatLon(52.15, 8.34).haversineDistance(clusterPoints[1]) < 2500)
        assertEquals(54416.0, tree.clusters[1].effectiveRadius, 1.0)
    }

    @Test
    fun `Seed cluster size of two clusters`() {
        val l = mutableListOf<LatLon>()
        // NL
        l.add(LatLon(lat = 52.75, lon = 4.87))

        // DU
        l.add(LatLon(lat = 51.84, lon = 7.72))

        val tree = FixedRadiusClusterer(l, radiusSizes=listOf(150_000.0, 175_000.0 ))

        print(tree.toGraphviz())
        assertEquals(2, tree.centers().size)
        assertTrue(LatLon(lat = 52.75, lon = 4.87).haversineDistance(tree.clusters[0].center) < 2500)
        assertEquals(0.0, tree.clusters[0].effectiveRadius)
        assertTrue(LatLon(lat = 51.84, lon = 7.72).haversineDistance(tree.clusters[1].center) < 2500)
        assertEquals(0.0, tree.clusters[0].effectiveRadius)
    }
}
