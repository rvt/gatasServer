package nl.rvantwisk.server.server.grpc

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Buffers elements from the upstream flow and emits them as lists when either the
 * buffer reaches a specified `maxSize` or a `timeout` duration has passed since
 * the last emission, whichever happens first.
 *
 * This operator is useful for scenarios where you want to process elements in batches,
 * but also want to ensure that elements are not held in the buffer indefinitely if the
 * flow of incoming elements is slow or sporadic.
 *
 * It uses a [channelFlow] to concurrently collect from the upstream and manage
 * timed flushes.
 *
 * @param timeout The maximum time to wait before emitting a buffered list, even if `maxSize` is not reached.
 * @param maxSize The maximum number of elements to buffer before emitting a list.
 * @return A [Flow] that emits lists of elements of type [T].
 */
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
