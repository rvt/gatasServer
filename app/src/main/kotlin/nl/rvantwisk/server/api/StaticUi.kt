package nl.rvantwisk.server.api

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.configureStaticUi() {
  routing {
    routing {
      staticResources("/", "static") {
        default("index.html")
      }
    }
  }
}
