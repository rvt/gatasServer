package nl.rvantwisk.server

import io.ktor.server.application.Application
import nl.rvantwisk.gatas.services.egm2008.Egm2008Reader
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import java.io.IOException

@Throws(IOException::class)
fun readByteArray(path: Path): ByteArray {
    FileSystem.RESOURCES.source(path).use { fileSource ->
        fileSource.buffer().use { bufferedFileSource ->
            return bufferedFileSource.readByteArray()
        }
    }
}

fun Application.configureEgm2008() {
    Egm2008Reader.init(readByteArray("./egm2008.bin".toPath()))
}
