package nl.rvantwisk.server

import io.ktor.server.application.Application
import nl.rvantwisk.server.feeds.Tile38Updater
import nl.rvantwisk.server.flowservices.KtorClient.getKoin

fun Application.configureTile38() {
    Tile38App.main()
}

object Tile38App {
    @JvmStatic
    fun main() {
        val updater = getKoin().get<Tile38Updater>()
        updater.start()
    }
}
