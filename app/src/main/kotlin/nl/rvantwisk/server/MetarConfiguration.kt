package nl.rvantwisk.server

import io.ktor.server.application.Application
import nl.rvantwisk.server.flowservices.KtorClient.getKoin
import nl.rvantwisk.server.metar.MetarUpdateService

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
