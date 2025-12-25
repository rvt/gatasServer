// AUTO-GENERATED FILE — DO NOT EDIT
package nl.rvantwisk.gatas.lib.services.egm2008

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

interface EarthGravitationalModel  {
    fun egmGeoidOffset(lat: Double, lon: Double): Int
}

class EGM2008 : EarthGravitationalModel, KoinComponent {
    private val log: Logger by inject { parametersOf(EGM2008::class.simpleName!!) }

    companion object Companion {
        const val MIN_LAT = -85.0
        const val MAX_LAT = 85.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0
        const val RESOLUTION_DEG = 0.5
        const val LAT_STEPS = ((MAX_LAT - MIN_LAT) / RESOLUTION_DEG).toInt() + 1
        const val LON_STEPS = ((MAX_LON - MIN_LON) / RESOLUTION_DEG).toInt() + 1
    }

    private var data: ByteArray? = null

    fun init(bytes: ByteArray) {
        require(bytes.size == LAT_STEPS * LON_STEPS) {
            "Invalid EGM2008 data size: expected ${LAT_STEPS * LON_STEPS}, got ${bytes.size}"
        }
        data = bytes
    }

    override fun egmGeoidOffset(lat: Double, lon: Double): Int {
        val bytes = data ?: error("EGM2008 not initialized. Call EGM2008.init() first.")

        if (lat !in MIN_LAT..MAX_LAT || lon !in MIN_LON..MAX_LON) {
            log.w { "Latitude or longitude out of bounds: lat=$lat, lon=$lon" }
            return 0
        }

        val latIdx = ((MAX_LAT - lat) / RESOLUTION_DEG).roundToInt()
        val lonIdx = ((lon - MIN_LON) / RESOLUTION_DEG).roundToInt()

        if (latIdx !in 0 until LAT_STEPS || lonIdx !in 0 until LON_STEPS) {
            log.w { "Index out of bounds: latIdx=$latIdx, lonIdx=$lonIdx" }
            return 0
        }

        val index = lonIdx * LAT_STEPS + latIdx
        return bytes[index].toInt()
    }
}
