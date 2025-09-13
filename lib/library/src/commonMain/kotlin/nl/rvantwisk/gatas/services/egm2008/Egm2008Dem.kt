// AUTO-GENERATED FILE — DO NOT EDIT
package nl.rvantwisk.gatas.services.egm2008

import kotlin.math.roundToInt

object Egm2008Reader {
    const val MIN_LAT = 35.0
    const val MAX_LAT = 72.0
    const val MIN_LON = -25.0
    const val MAX_LON = 45.0
    const val RESOLUTION_DEG = 0.5
    const val LAT_STEPS = ((MAX_LAT - MIN_LAT) / RESOLUTION_DEG).toInt() + 1
    const val LON_STEPS = ((MAX_LON - MIN_LON) / RESOLUTION_DEG).toInt() + 1

    private var data: ByteArray? = null

    val isInitialized: Boolean = data != null

    fun init(bytes: ByteArray) {
        if (data == null) {
            require(bytes.size == LAT_STEPS * LON_STEPS) {
                "Invalid EGM2008 data size: expected ${LAT_STEPS * LON_STEPS}, got ${bytes.size}"
            }
            data = bytes
        }
    }

    fun egmGeoidOffset(lat: Double, lon: Double): Int {
        val bytes = data ?: error("Egm2008Reader not initialized. Call Egm2008Reader.init() first.")

        if (lat !in MIN_LAT..MAX_LAT || lon !in MIN_LON..MAX_LON) {
            println("Latitude or longitude out of bounds: lat=$lat, lon=$lon")
            return 0
        }

        val latIdx = ((MAX_LAT - lat) / RESOLUTION_DEG).roundToInt()
        val lonIdx = ((lon - MIN_LON) / RESOLUTION_DEG).roundToInt()

        if (latIdx !in 0 until LAT_STEPS || lonIdx !in 0 until LON_STEPS) {
            println("Index out of bounds: latIdx=$latIdx, lonIdx=$lonIdx")
            return 0
        }

        val index = lonIdx * LAT_STEPS + latIdx
        return bytes[index].toInt()
    }
}
