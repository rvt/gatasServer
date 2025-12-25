package nl.rvantwisk.gatas.lib.utils

import nl.rvantwisk.gatas.lib.math.haversineDistance
import nl.rvantwisk.gatas.lib.models.LatLon

/**
 * FixedRadiusClusterer groups geographic points (lat/lon) into clusters
 * using a predefined, finite set of allowed radii.
 *
 * <p>
 * The clusterer is designed for scenarios where downstream systems
 * (e.g. external web services, spatial queries, or storage backends)
 * can only handle requests up to specific maximum radii.
 * </p>
 *
 * <h2>Core principles</h2>
 * <ul>
 *   <li>Each configured radius may be used at most once.</li>
 *   <li>Larger radii are preferred over smaller ones.</li>
 *   <li>Clusters that cannot be fully covered by smaller radii
 *       are prioritized for larger radii.</li>
 *   <li>Once a point is assigned to a cluster, it is never reused.</li>
 * </ul>
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 *   <li>Radii are processed from largest to smallest.</li>
 *   <li>For each radius, the algorithm tries all remaining points as
 *       potential seeds and builds a maximal cluster using iterative
 *       centroid expansion.</li>
 *   <li>The best cluster is selected based on:
 *     <ul>
 *       <li>Whether it is unavoidable (cannot be covered by smaller radii)</li>
 *       <li>Number of points covered</li>
 *     </ul>
 *   </li>
 *   <li>The selected cluster is removed from further consideration.</li>
 *   <li>Remaining points are clustered using the smallest configured radius.</li>
 * </ol>
 *
 * <h2>Centroid and effective radius</h2>
 * <p>
 * Each cluster’s center is the geographic centroid (average latitude
 * and longitude) of its points.
 * </p>
 *
 * <p>
 * The cluster’s effective radius is computed as the maximum haversine
 * distance from the centroid to any point in the cluster. This represents
 * the minimal radius required to fully cover the cluster.
 * </p>
 *
 * <h2>Important notes</h2>
 * <ul>
 *   <li>The build radius is a construction constraint, not a guarantee.</li>
 *   <li>The effective radius is the only value that should be used when
 *       deciding whether a cluster can be handled by a web service.</li>
 *   <li>This class is deterministic for a fixed input set.</li>
 *   <li>This is not a general-purpose clustering algorithm (e.g. DBSCAN or k-means).</li>
 * </ul>
 *
 * <h2>Typical use cases</h2>
 * <ul>
 *   <li>Partitioning aircraft positions for web services with fixed query limits</li>
 *   <li>Minimizing the number of spatial queries while ensuring full coverage</li>
 *   <li>Assigning spatial work units to multiple providers</li>
 * </ul>
 *
 * @param points
 *   The geographic points to cluster.
 *
 * @param radiusSizes
 *   A finite list of allowed radii (in meters). Each radius may be used
 *   at most once. Radii are processed from largest to smallest.
 */

class FixedRadiusClusterer(
    private val points: List<LatLon>,
    radiusSizes: List<Double>
) {

    val smallestClusterSize = radiusSizes.min()

    data class Cluster(
        val center: LatLon,
        val points: List<LatLon>,
        //val radius: Double,
        val effectiveRadius: Double
    )

    private val radii = radiusSizes.sortedDescending()

    val clusters: List<Cluster> = buildClusters()

    private fun buildClusters(): List<Cluster> {
        val unclustered = points.toMutableList()
        val result = mutableListOf<Cluster>()

        // Use each configured radius exactly once
        for (radius in radii) {
            if (unclustered.isEmpty()) break

            val smallerRadii = radii.filter { it < radius }

            val bestCluster = unclustered
                .map { seed ->
                    buildClusterFromSeed(seed, unclustered, radius)
                }
                .maxByOrNull { cluster ->
                    val unavoidable = !canBeCoveredBySmallerRadii(
                        cluster.points,
                        smallerRadii
                    )

                    // Strongly prefer unavoidable clusters
                    (if (unavoidable) 10_000 else 0) + cluster.points.size
                }

            if (bestCluster != null && bestCluster.points.size > 1) {
                result.add(bestCluster)
                unclustered.removeAll(bestCluster.points)
            }
        }

        // Fallback clustering for remaining points
        while (unclustered.isNotEmpty()) {
            val cluster = buildClusterFromSeed(
                seed = unclustered.first(),
                candidates = unclustered,
                radius = smallestClusterSize
            )
            result.add(cluster)
            unclustered.removeAll(cluster.points)
        }

        return result
    }

    /**
     * Build a maximal cluster using iterative centroid expansion
     */
    private fun buildClusterFromSeed(
        seed: LatLon,
        candidates: List<LatLon>,
        radius: Double
    ): Cluster {
        val clusterPoints = mutableListOf(seed)
        val remaining = candidates.toMutableList().apply { remove(seed) }

        var changed: Boolean
        do {
            changed = false
            val centroid = centroidOf(clusterPoints)

            val within = remaining.filter {
                centroid.haversineDistance(it) <= radius
            }

            if (within.isNotEmpty()) {
                clusterPoints.addAll(within)
                remaining.removeAll(within)
                changed = true
            }
        } while (changed)

        val center = centroidOf(clusterPoints)
        val effectiveRadius = effectiveRadius(center, clusterPoints)

        return Cluster(
            center = center,
            points = clusterPoints,
            //radius = radius,
            effectiveRadius = effectiveRadius
        )
    }

    /**
     * Determines whether a set of points can be fully clustered
     * using only the provided smaller radii (no fallback allowed).
     */
    private fun canBeCoveredBySmallerRadii(
        points: List<LatLon>,
        smallerRadii: List<Double>
    ): Boolean {
        if (points.isEmpty()) return true
        if (smallerRadii.isEmpty()) return false

        val clusterer = FixedRadiusClusterer(
            points = points,
            radiusSizes = smallerRadii
        )

        val covered = clusterer.clusters
            .flatMap { it.points }
            .toSet()

        return covered.size == points.size
    }

    private fun centroidOf(points: List<LatLon>): LatLon {
        val lat = points.map { it.lat }.average()
        val lon = points.map { it.lon }.average()
        return LatLon(lat, lon)
    }

    private fun effectiveRadius(center: LatLon, points: List<LatLon>): Double =
        points.maxOf { center.haversineDistance(it) }

    fun centers(): List<LatLon> = clusters.map { it.center }

    fun toGraphviz(): String {
        val sb = StringBuilder()
        sb.appendLine("digraph Clusters {")
        sb.appendLine("  node [shape=record fontname=\"Helvetica\"];")

        clusters.forEachIndexed { i, cluster ->
            val label = buildString {
                append("{Cluster $i")
                append(" | er=${(cluster.effectiveRadius / 1000).toInt()}km")
                append(" | center=${"%.5f".format(cluster.center.lat)},${"%.5f".format(cluster.center.lon)}")
                append(" | n=${cluster.points.size}")
                append("|")
                append(cluster.points.joinToString("\\n") {
                    "${"%.5f".format(it.lat)},${"%.5f".format(it.lon)}"
                })
                append("}")
            }
            sb.appendLine("  c$i [label=\"$label\"]")
        }

        sb.appendLine("}")
        return sb.toString()
    }
}

fun List<FixedRadiusClusterer.Cluster>.sortedByDistanceFrom(ref: LatLon): List<FixedRadiusClusterer.Cluster> =
    this.sortedBy { ref.haversineDistance(it.center) }
