package nl.rvantwisk.server

import io.ktor.server.application.Application
import nl.rvantwisk.server.flowservices.KtorClient.getKoin
import nl.rvantwisk.server.udp.UdpAircraftService

fun Application.configureUdpService() {
    UdpService.main()
}

object UdpService {
    @JvmStatic
    fun main() {
        val updater = getKoin().get<UdpAircraftService>()
        updater.start()
    }
}