package com.example

//class Tile38Container(imageName: String = "tile38/tile38:latest") :
//  GenericContainer<Tile38Container>(DockerImageName.parse(imageName))
//
//class Tile38IntegrationTest {
//
//  companion object {
//    private val tile38 = Tile38Container().apply {
//      withExposedPorts(9851) // Tile38 default port
//      start()
//    }
//  }
//
//  @Test
//  fun `test connection to tile38`() {
//    val host = tile38.host
//    val port = tile38.getMappedPort(9851)
//
//    // Here you can connect to Tile38 via Redis/TCP client
//    println("Tile38 running at $host:$port")
//  }
//}
