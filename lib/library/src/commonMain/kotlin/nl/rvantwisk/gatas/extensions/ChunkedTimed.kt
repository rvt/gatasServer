package nl.rvantwisk.server.server.grpc

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

fun <T> Flow<T>.chunkedTimed(
    timeout: Duration,
    maxSize: Int
): Flow<List<T>> = channelFlow {
    val buffer = mutableListOf<T>()

    suspend fun flush() {
        if (buffer.isNotEmpty()) {
            send(buffer.toList())
            buffer.clear()
        }
    }

    // Collect incoming elements
    launch {
        collect {
            buffer.add(it)
            if (buffer.size >= maxSize) flush()
        }
    }

    // Periodically flush based on timeout
    launch {
        while (isActive) {
            delay(timeout)
            flush()
        }
    }
}