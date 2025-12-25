package nl.rvantwisk.gatas.it

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class Tile38Container(imageName: String = "tile38/tile38:latest") :
    GenericContainer<Tile38Container>(DockerImageName.parse(imageName))

open class Tile38BaseIT {

    companion object Companion {
        const val TILE38_PORT = 9851

        // Define the container in the companion object for a shared lifecycle across all tests in this class
        @JvmStatic
        val tile38 = Tile38Container().apply {
            withExposedPorts(TILE38_PORT)
        }

        @BeforeAll
        @JvmStatic
        fun startContainer() {
            tile38.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainer() {
            print("= tile38.stop() ======================================================")
            tile38.stop()
        }
    }
}
