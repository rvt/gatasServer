package nl.rvantwisk.server

import io.ktor.server.application.Application
import nl.rvantwisk.server.flowservices.KtorClient.getKoin
import nl.rvantwisk.server.tcp.TcpAircraftService
import nl.rvantwisk.server.udp.UdpAircraftService

fun Application.configureTcpService() {
    TcpService.main()
}

object TcpService {
    @JvmStatic
    fun main() {
        val updater = getKoin().get<TcpAircraftService>()
        updater.start()
    }
}
