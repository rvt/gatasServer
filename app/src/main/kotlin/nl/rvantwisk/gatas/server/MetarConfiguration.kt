package nl.rvantwisk.gatas.server

import io.ktor.server.application.*
import nl.rvantwisk.gatas.server.flowservices.KtorClient.getKoin
import nl.rvantwisk.gatas.server.metar.MetarUpdateService

fun Application.configureMetarService() {
  MetarConfiguration.main()
}

object MetarConfiguration {
    @JvmStatic
    fun main() {
        val updater = getKoin().get<MetarUpdateService>()
        updater.start()
    }
}
