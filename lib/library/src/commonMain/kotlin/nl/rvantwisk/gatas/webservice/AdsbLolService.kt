package nl.rvantwisk.gatas.webservice

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import nl.rvantwisk.gatas.extensions.FTPMIN_TO_MS
import nl.rvantwisk.gatas.extensions.KN_TO_MS
import nl.rvantwisk.gatas.extensions.MAX_CALLSIGN_LENGTH
import nl.rvantwisk.gatas.extensions.RAD_TO_DEGREES
import nl.rvantwisk.gatas.extensions.adsbToGatasCategory
import nl.rvantwisk.gatas.extensions.footToMeter
import nl.rvantwisk.gatas.extensions.hexToUint
import nl.rvantwisk.gatas.extensions.meterToNauticalMiles
import nl.rvantwisk.gatas.models.AddressType
import nl.rvantwisk.gatas.models.AircraftCategory
import nl.rvantwisk.gatas.models.AircraftPosition
import nl.rvantwisk.gatas.models.DataSource
import nl.rvantwisk.gatas.webservice.models.AdsbLolAircraftDto
import nl.rvantwisk.gatas.webservice.models.AdsbLolResponseDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private val log = Logger.withTag(AdsbLolService::class.simpleName ?: "AdsbLolService")

class AdsbLolService : AircraftWebService, KoinComponent {
    private val adsbHttpClient: HttpClient by inject(named("adsbClient"));

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetchPositions(
        latitude: Double,
        longitude: Double,
        radiusM: Double
    ): List<AircraftPosition> {
        val radiusNm = radiusM.meterToNauticalMiles().toInt()
        log.i { "Fetching aircraft from ${getName()} for radius: ${radiusNm}Nm my position: ${latitude} ${longitude}" }
        try {
            val req = "https://api.adsb.lol/v2/point/${latitude}/${longitude}/${radiusNm}"
            val response =
                adsbHttpClient.get(req) {
                    accept(ContentType.Application.Json)

//                    // Add logging interceptor
//                    log.i {
//                        """Sending request to:
//                      |URL: ${url.buildString()}
//                      |Headers: ${headers.entries()}
//                    """.trimMargin()
//                    }
                }.body<AdsbLolResponseDto>()

            return response.ac.map { dto ->
                    // hex codes prefixed with a ~ are received byTSIS-B traffic, usually mode-c transponder, we remove the ~
                    // assuming the hexcode will always be teh same random number for the same aircraft
                    val hex = dto.hex.filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }

                    AircraftPosition(
                        dataSource = DataSource.ADSB,
                        addressType = AddressType.ICAO,
                        latitude = dto.lat ?: 0.0,
                        longitude = dto.lon ?: 0.0,
                        _ellipsoidHeight = dto.altGeom?.let { (it.footToMeter()).toInt() },
                        _baroAltitude = dto.altBaro,
                        course = dto.track ?: dto.trueHeading ?: dto.magHeading ?: dto.navHeading ?: 0.0,
                        hTurnRate = (dto.trackRate ?: 0.0) * RAD_TO_DEGREES,
                        groundSpeed = (dto.gs ?: 0.0) * KN_TO_MS,
                        verticalSpeed = (dto.geomRate ?: 0.0) * FTPMIN_TO_MS,
                        aircraftCategory = dto.category?.adsbToGatasCategory() ?: AircraftCategory.UNKNOWN,
                        id = hex.hexToUint(),
                        callSign = dto.composedCallSignType(),
                        qnh = dto.navQnh,
                        ellipsoidHeight = 0,
                        nicBaro = dto.nicBaro ?: 0,
                    )
                }

        } catch (e: Exception) {
            log.e { "Error ${e}" }
        }
        throw Exception("Fail")
    }

    override fun getName() = "adsb.lol"
}

/**
 * Create a callsign consiting of the callsign and aircraft type
 * returns for example: PH-ABC!C152
 * Maximum length will be 12 BYTES
 */
fun AdsbLolAircraftDto.composedCallSignType(): String {
    return if (this.t?.isNotEmpty() == true) {
        "${r?.trim()}!${t.trim()}"
    } else {
        r?.trim() ?: "-"
    }.take(MAX_CALLSIGN_LENGTH)
}
