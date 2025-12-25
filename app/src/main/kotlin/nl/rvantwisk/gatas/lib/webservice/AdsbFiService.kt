package nl.rvantwisk.gatas.lib.webservice

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import nl.rvantwisk.gatas.lib.extensions.*
import nl.rvantwisk.gatas.lib.models.AddressType
import nl.rvantwisk.gatas.lib.models.AircraftCategory
import nl.rvantwisk.gatas.lib.models.AircraftPosition
import nl.rvantwisk.gatas.lib.models.DataSource
import nl.rvantwisk.gatas.lib.webservice.models.AdsbFiResponseDto
import nl.rvantwisk.gatas.lib.webservice.models.composedCallSignType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

class AdsbFiService : AircraftWebService, KoinComponent {
    private val adsbHttpClient: HttpClient by inject(named("adsbClient"));

    private val log: Logger by inject { parametersOf(AdsbFiService::class.simpleName!!) }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetchPositions(
        latitude: Double,
        longitude: Double,
        radiusM: Double
    ): List<AircraftPosition> {
        val radiusNm = radiusM.meterToNauticalMiles().toInt()
        log.i { "Fetching aircraft from ${getName()} for radius: ${radiusNm}Nm my position: ${latitude} ${longitude}" }
        try {
            val req = "https://opendata.adsb.fi/api/v2/lat/${latitude}/lon/${longitude}/dist/${radiusNm}"
            val response =
                adsbHttpClient.get(req) {
                    accept(ContentType.Application.Json)

                    // Add logging interceptor
//                    log.i {
//                        """Sending request to:
//                      |URL: ${url.buildString()}
//                      |Headers: ${headers.entries()}
//                    """.trimMargin()
//                    }
                }.body<AdsbFiResponseDto>()

            return response.aircraft

                .map { dto ->
                    // hex codes prefixed with a ~ are received byTSIS-B traffic, usually mode-c transponder, we remove the ~
                    // assuming the hexcode will always be the same random number for the same aircraft
                    val hex = dto.hex.filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }

                    AircraftPosition(
                        dataSource = DataSource.ADSB,
                        addressType = AddressType.ICAO,
                        latitude = dto.lat ?: 0.0,
                        longitude = dto.lon ?: 0.0,
                        course = dto.track ?: dto.trueHeading ?: dto.magHeading ?: dto.navHeading ?: 0.0,
                        hTurnRate = (dto.trackRate ?: 0.0) * RAD_TO_DEGREES,
                        groundSpeed = (dto.gs ?: 0.0) * KN_TO_MS,
                        verticalSpeed = (dto.geomRate ?: 0.0) * FTPMIN_TO_MS,
                        aircraftCategory = dto.category?.adsbToGatasCategory() ?: AircraftCategory.UNKNOWN,
                        id = hex.hexToUint(),
                        callSign = dto.composedCallSignType(),
                        qnh = dto.navQnh,
                        nicBaro = dto.nicBaro ?: 0,
                        baroAltitude = if (dto.altBaro == "ground") { null } else { dto.altBaro?.toIntOrNull()?.footToMeter() },
                        ellipsoidHeight = dto.altGeom?.let { (it.footToMeter()) },
                        isGround = dto.altBaro == "ground"
                    )
                }

        } catch (e: Exception) {
            log.e { "Error ${e}" }
        }
        throw Exception("Fai")
    }

    override fun getName() = "adsb.fi"
    override fun getMaxRadius(): Double = 100000 * NM_TO_METERS
}

