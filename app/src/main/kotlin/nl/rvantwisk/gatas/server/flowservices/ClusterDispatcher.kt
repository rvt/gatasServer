package nl.rvantwisk.gatas.server.flowservices

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.models.FlowResult
import nl.rvantwisk.gatas.lib.models.FlowStatus
import nl.rvantwisk.gatas.lib.models.LatLon
import nl.rvantwisk.gatas.lib.utils.FixedRadiusClusterer
import nl.rvantwisk.gatas.lib.webservice.AircraftWebService
import nl.rvantwisk.gatas.server.datastore.SpatialService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

data class ClusterState(
    val cluster: FixedRadiusClusterer.Cluster,
    var lastFetchedAt: Long = 0L
)

private data class WebServiceState(
    val ws: AircraftWebService,
    var lastCallAt: Long = 0L
)

/**
 * The ClusterDispatcher (for lack of better name) will take in fleet data and calculate the
 * clusters where aircraft are flying in. As a result it will use the correct webservices
 * to fetch life aircraft data.
 */
class ClusterDispatcher(
    private val webServices: List<AircraftWebService>,
    private val spatialService: SpatialService,

    val fleetCheckInterval: Long = 5000L,
    val clusterInterval: Long = 60_000L,
    val minWSRequestInterval: Long = 1_050L
) : KoinComponent {

    private val log: Logger by inject { parametersOf(ClusterDispatcher::class.simpleName!!) }
//    private val spatialService: SpatialService by inject(named("SpatialService"))
//    private val webServices: List<AircraftWebService> by inject()


    private val clusterStates = mutableMapOf<Int, ClusterState>()
    private val webServiceStates = mutableMapOf<String, WebServiceState>()

    fun getClusterStates() = clusterStates.values.toList().map { it.copy() }

    fun streamAircraft(): Flow<FlowResult<List<AircraftPosition>>> = flow {
        var lastClusterTime = 0L
        var clusters: List<FixedRadiusClusterer.Cluster> = emptyList()

        val radii = webServices.map { it.getMaxRadius() }

        // Init WS state once
        webServices.forEach {
            webServiceStates[it.getName()] = WebServiceState(it)
        }

        while (currentCoroutineContext().isActive) {
            // Using 500 instead of Long.MAX_VALUE  to ensure we don't pull the whole DB
            val fleet = spatialService.scanFleet(500)
            val now = System.currentTimeMillis()

            if (fleet.isEmpty()) {
                delay(fleetCheckInterval)
                continue
            }

            if (clusters.isEmpty() || now - lastClusterTime > clusterInterval) {
                val clusterer = FixedRadiusClusterer(
                    points = fleet.map { LatLon(it.latitude, it.longitude) },
                    radiusSizes = radii
                )

                clusters = clusterer.clusters
                lastClusterTime = now

                clusters.forEachIndexed { idx, cluster ->
                    clusterStates.putIfAbsent(idx, ClusterState(cluster))
                }
            }

            val assignments = computeAssignments(clusters, now, minWSRequestInterval)

            assignments.forEach { (clusterState, wsState) ->
                try {
                    val FETCH_RADIUS_BUFFER_M = 25_000.0
                    val MIN_FETCH_RADIUS_M = 100_000.0

                    val requestedRadius =
                        clusterState.cluster.effectiveRadius + FETCH_RADIUS_BUFFER_M

                    val radiusToFetch =
                        requestedRadius
                            .coerceAtMost(wsState.ws.getMaxRadius())
                            .coerceAtLeast(MIN_FETCH_RADIUS_M)

                    val positions = withTimeout(750) {
                        wsState.ws.fetchPositions(
                            latitude = clusterState.cluster.center.lat,
                            longitude = clusterState.cluster.center.lon,
                            radiusM = radiusToFetch
                        )
                    }

                    clusterState.lastFetchedAt = now
                    wsState.lastCallAt = now

                    emit(
                        FlowResult(
                            source = wsState.ws.getName(),
                            status = FlowStatus.SUCCESS,
                            data = positions
                        )
                    )
                } catch (e: CancellationException) {
                    emit(
                        FlowResult(
                            source = wsState.ws.getName(),
                            status = FlowStatus.TIMEOUT,
                            message = e.message
                        )
                    )
                } catch (e: Exception) {
                    emit(
                        FlowResult(
                            source = wsState.ws.getName(),
                            status = FlowStatus.FAILURE,
                            message = e.message
                        )
                    )
                }

                delay(50)
            }

            delay(minWSRequestInterval)
        }
    }

    private fun computeAssignments(
        clusters: List<FixedRadiusClusterer.Cluster>,
        now: Long,
        minWSRequestInterval: Long
    ): List<Pair<ClusterState, WebServiceState>> {

        val result = mutableListOf<Pair<ClusterState, WebServiceState>>()

        val wsStates = webServiceStates.values.toList()
        val clusterStatesSorted = clusterStates.values.sortedBy { it.lastFetchedAt }

        // CASE 1: fewer clusters than webservices → duplicate fetch
        if (clusters.size <= wsStates.size) {
            val clusterState = clusterStatesSorted.first()
            wsStates.forEach { wsState ->
                if (now - wsState.lastCallAt >= minWSRequestInterval) {
                    result.add(clusterState to wsState)
                }
            }
            return result
        }

        // CASE 2: more clusters than webservices → fair rotation
        val wsByAvailability = wsStates.sortedBy { it.lastCallAt }

        for (wsState in wsByAvailability) {
            if (now - wsState.lastCallAt < minWSRequestInterval) continue

            val clusterState = clusterStatesSorted.firstOrNull() ?: continue
            result.add(clusterState to wsState)
        }

        return result
    }
}
