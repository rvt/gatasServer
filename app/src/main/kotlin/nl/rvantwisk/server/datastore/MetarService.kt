package nl.rvantwisk.server.datastore

import co.touchlab.kermit.Logger
import com.uber.h3core.H3Core
import nl.rvantwisk.server.H3_RESOLUTION
import nl.rvantwisk.server.STD_QNH
import nl.rvantwisk.server.metar.model.MetarH3
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

interface MetarCache {
  suspend fun getQNH(lat: Double, lon: Double): Double
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


    // somewhere we need to fix the error in the log :  position: 1:229427: Field 'elevation_m' is required for type with serial na

    override suspend fun getQNH(lat: Double, lon: Double): Double {
      val h3Id = h3.latLngToCell(lat, lon, H3_RESOLUTION)

      // Check local in-memory cache
      metarCache[h3Id]?.qnh?.let { return it }

      // Check persistent H3 cache
      val metarH3 = tile38.getFieldAs<MetarH3>("metarh3", h3Id, "json")
      if (metarH3 != null) {
        return metarH3.qnh
      }

      // Find nearest live METAR
      val nearby = tile38.getNearbyMetar(lat, lon).firstOrNull()
      return if (nearby != null) {
        tile38.addMetarById(nearby, "metarh3", h3Id)
        nearby.qnh
      } else {
        STD_QNH  // Fallback to standard if nothing found
      }
    }
  }

  /**
   * Call this function each time you need to handle a request
   * The returned cache will help to reduce the number of requests because
   * most aircraft will be found in the same sector and thus avoiding over the wire requests to a service
   */
  fun cacheFactory(): LocalCache {
    return LocalCache(tile38, h3);
  }

}
