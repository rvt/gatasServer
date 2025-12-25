package nl.rvantwisk.gatas.server

import io.ktor.server.application.*
import nl.rvantwisk.gatas.server.feeds.AircraftFlowToTile38
import nl.rvantwisk.gatas.server.flowservices.KtorClient.getKoin

fun Application.configureTile38() {
    Tile38App.main()
}

object Tile38App {
    @JvmStatic
    fun main() {
        val updater = getKoin().get<AircraftFlowToTile38>()
        updater.start()
    }
}
