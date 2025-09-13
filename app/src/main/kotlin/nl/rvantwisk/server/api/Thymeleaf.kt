package nl.rvantwisk.server.api
//
//import io.ktor.server.application.Application
//import io.ktor.server.application.install
//import io.ktor.server.response.respond
//import io.ktor.server.routing.get
//import io.ktor.server.routing.routing
//import io.ktor.server.thymeleaf.Thymeleaf
//import io.ktor.server.thymeleaf.ThymeleafContent
//import org.thymeleaf.templatemode.TemplateMode
//import org.thymeleaf.templateresolver.FileTemplateResolver
//
//@OptIn(ExperimentalStdlibApi::class)
//fun Application.configureThymeleaf() {
//
//  install(Thymeleaf) {
//
//    setTemplateResolver(FileTemplateResolver().apply {
//      prefix = "src/main/resources/templates/thymeleaf/"
//      suffix = ".html"
//      characterEncoding = "utf-8"
//      isCacheable = false   // turn off caching in dev
//      templateMode = TemplateMode.HTML
//    })
//  }
//
//  routing {
//    get("/") {
//      call.respond(ThymeleafContent("index", emptyMap()))
//    }
//  }
//}
