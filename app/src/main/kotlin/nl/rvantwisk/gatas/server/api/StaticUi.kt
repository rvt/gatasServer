package nl.rvantwisk.gatas.server.api

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

/**
 * Configures the Ktor application to serve static UI resources.
 *
 * This extension function sets up routing to serve files from the "static"
 * resources folder. It also specifies "index.html" as the default file
 * to serve when a directory is requested.
 */
fun Application.configureStaticUi() {
  routing {
    routing { // Consider removing this nested routing block if it's not strictly necessary for your structure.
      staticResources("/", "static") {
        default("index.html")
      }
    }
  }
}
