package nl.rvantwisk.server.datastore

import co.touchlab.kermit.Logger
import com.uber.h3core.H3Core
import nl.rvantwisk.server.H3_RESOLUTION
import nl.rvantwisk.server.STD_QNH
import nl.rvantwisk.server.metar.model.MetarH3
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named


private val log = Logger.withTag(MetarService::class.simpleName ?: "MetarService")

interface MetarCache {
  suspend fun getMetar(lat: Double, lon: Double): Double
}

class MetarService : KoinComponent {

  private val tile38: SpatialService by inject(named("SpatialService"))
  private val h3: H3Core by inject()

  /**
   * Local cache used to store aircraft for a given lat/lon gor
   * For now, grab a new version for each request Since most aircraft in yoru area will use the same h3Id
   * I expect this local cache to be used a lot.
   */
  class LocalCache(val tile38: SpatialService, val h3: H3Core) : MetarCache {
    val metarCache: MutableMap<Long, MetarH3> = mutableMapOf()

    override suspend fun getMetar(lat: Double, lon: Double): Double {
      val h3Id = h3.latLngToCell(lat, lon, H3_RESOLUTION)

      // Check local cache
      if (metarCache.containsKey(h3Id)) {
        return metarCache[h3Id]!!.qnh
      }

      // Check h3 cache in Tile38
      // Optionally we could use REDS to lookup this item
      val metarH3 = tile38.getFieldAs<MetarH3>("metarh3", h3Id, "json")

      // Locale closest metar
      if (metarH3 == null) {
        val nearbyMetar = tile38.getNearbyMetar(lat, lon)
        if (nearbyMetar.isNotEmpty()) {
          // Optionally we could store the h3 entry in REDIS
          tile38.addMetarById(nearbyMetar.first(), "metarh3", h3Id)

// Used for debugging only
//          val h3Address = h3.latLngToCellAddress(lat, lon, H3_RESOLUTION)
//          tile38.addMetarById(nearbyMetar.first(), "metarh3addr", h3Address)
        }
        // Return standard if there was no local metar
        return STD_QNH;
      }

      return metarH3.qnh
    }
  }

  /**
   * Call this function each time you need to handle a request
   */
  fun getCache(): LocalCache {
    return LocalCache(tile38, h3);
  }

}
