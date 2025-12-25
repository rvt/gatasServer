package nl.rvantwisk.gatas.server.datastore

import com.uber.h3core.H3Core
import nl.rvantwisk.gatas.lib.extensions.STD_QNH
import nl.rvantwisk.gatas.server.H3_AIRCRAFT_CELL_SIZE
import nl.rvantwisk.gatas.server.datastore.tile38.models.MetarH3
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

interface MetarCache {
  suspend fun getQNH(lat: Double, lon: Double): Double
}

/**
 * Cache service that provides a temporary cache between the backend like Tile38 or Redis
 * and the current client that receives aircraft data. Always request a new cache per
 * GA/TAS request.
 */
class MetarCacheFactoryService : KoinComponent {
  private val tile38: SpatialService by inject(named("SpatialService"))
  private val h3: H3Core by inject()

  /**
   * Local cache used to store aircraft for a given lat/lon gor
   * For now, grab a new version for each request Since most aircraft in yoru area will use the same h3Id
   * I expect this local cache to be used a lot.
   */
  class LocalCacheImpl(val tile38: SpatialService, val h3: H3Core) : MetarCache {
    val metarCache: MutableMap<Long, MetarH3> = mutableMapOf()

    override suspend fun getQNH(lat: Double, lon: Double): Double {
      val h3Id = h3.latLngToCell(lat, lon, H3_AIRCRAFT_CELL_SIZE)

      // Check local in-memory cache
      metarCache[h3Id]?.qnh?.let { return it }

      // Check persistent H3 cache
      val metarH3 = tile38.getMetarByH3(h3Id)
      if (metarH3 != null) {
        return metarH3.qnh
      }

      // Find nearest live METAR
      val nearby = tile38.getNearbyMetar(lat, lon).firstOrNull()
      return if (nearby != null) {
        tile38.addMetarByH3(nearby, h3Id)
        nearby.qnh
      } else {
        STD_QNH  // Fallback to standard if nothing found
      }
    }
  }

  /**
   * Call this function each time you need to handle a request
   * The returned cache will help to reduce the number of requests to a backend
   */
  fun cacheFactory(): LocalCacheImpl {
    return LocalCacheImpl(tile38, h3);
  }

}
