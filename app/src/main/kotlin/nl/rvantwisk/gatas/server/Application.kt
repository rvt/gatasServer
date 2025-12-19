package nl.rvantwisk.gatas.server

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import nl.rvantwisk.gatas.server.api.configureApi
import nl.rvantwisk.gatas.server.api.configureStaticUi

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  configureEgm2008()
  configureMonitoring()
  configureFrameworks()
  configureAdministration()
  configureStaticUi()
  configureApi()
  configureTile38()
  configureUdpService()
  configureMetarService()
}
