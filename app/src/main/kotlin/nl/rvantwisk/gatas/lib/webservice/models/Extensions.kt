package nl.rvantwisk.gatas.lib.webservice.models

import nl.rvantwisk.gatas.lib.extensions.MAX_CALLSIGN_LENGTH


private fun calculateComposedCallSign(r: String?, t: String?): String {
    return if (t?.isNotEmpty() == true) {
//        "${r?.trim()}!${t.trim()}"
        r?.trim() ?: "-"
    } else {
        r?.trim() ?: "-"
    }.take(MAX_CALLSIGN_LENGTH)
}

/**
 * Create a callsign consiting of the callsign and aircraft type
 * returns for example: PH-ABC!C152
 * Maximum length will be 12 BYTES
 */
fun AirplanesLiveAircraftDto.composedCallSignType(): String =
    calculateComposedCallSign(r, t)

fun AdsbFiAircraftDto.composedCallSignType(): String =
    calculateComposedCallSign(r, t)
