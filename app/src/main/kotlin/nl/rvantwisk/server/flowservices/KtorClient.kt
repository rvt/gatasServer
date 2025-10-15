package nl.rvantwisk.server.flowservices

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application.TYPE
import io.ktor.serialization.Configuration
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import io.ktor.serialization.kotlinx.xml.DefaultXml
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.component.KoinComponent

object KtorClient : KoinComponent {

  /**
   * Simple client to be used to fetch JSON data from webservices
   */
  fun getHttpClient(timeout: Long = 1500): HttpClient = HttpClient {
    install(HttpTimeout) {
      requestTimeoutMillis = timeout
      connectTimeoutMillis = 1500
      socketTimeoutMillis = 900
    }

    install(ContentEncoding) {
      gzip()
      deflate()
      identity()
    }

//    install(Logging) {
//      logger = io.ktor.client.plugins.logging.Logger.DEFAULT
//      sanitizeHeader { header -> header == HttpHeaders.Authorization }
//    }

    install(ContentNegotiation) {
      json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
      })
    }

    install(DefaultRequest) {
      headers.append("Accept", "application/json, */*;q=0.8")
      headers.append("Accept-Encoding", "gzip, deflate")
      headers.append("User-Agent", "GA/TAS Server http://github.com/rvt/openace")
    }
  }

  /**
   * Simple client to be used to fetch XML data from webservices
   */
  fun getHttpXmlClient(timeout: Long = 1500): HttpClient = HttpClient {
    install(HttpTimeout) {
      requestTimeoutMillis = timeout
      connectTimeoutMillis = 1500
      socketTimeoutMillis = 900
    }

    install(DefaultRequest) {
      headers.append("Accept", "application/xml, */*;q=0.8")
      headers.append("Accept-Encoding", "gzip, deflate")
      headers.append("User-Agent", "GATAS Server http://github.com/rvt/openace")
    }
  }
}

