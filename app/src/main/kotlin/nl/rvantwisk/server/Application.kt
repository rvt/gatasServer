package nl.rvantwisk.server

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import nl.rvantwisk.server.api.configureApi
import nl.rvantwisk.server.api.configureStaticUi

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  configureEgm2008()
  configureMonitoring()
  configureFrameworks()
  configureAdministration()
//  configureTemplating()
  configureStaticUi()
  configureApi()
  configureTile38()
  configureUdpService()
  configureTcpService()
  configureMetarService()
}
