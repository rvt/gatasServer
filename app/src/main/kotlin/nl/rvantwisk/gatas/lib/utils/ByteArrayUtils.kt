package nl.rvantwisk.gatas.lib.utils

import okio.FileSystem
import okio.Path
import okio.buffer

fun readByteArray(path: Path): ByteArray {
    FileSystem.RESOURCES.source(path).use { fileSource ->
        fileSource.buffer().use { bufferedFileSource ->
            return bufferedFileSource.readByteArray()
        }
    }
}
