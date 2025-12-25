package nl.rvantwisk.gatas.server

import io.ktor.server.application.*
import io.ktor.server.netty.*
import nl.rvantwisk.gatas.server.api.configureApi
import nl.rvantwisk.gatas.server.api.configureStaticUi

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  configureMonitoring()
  configureFrameworks()
  configureAdministration()
  configureStaticUi()
  configureApi()
  configureTile38()
  configureUdpService()
  configureMetarService()
}
