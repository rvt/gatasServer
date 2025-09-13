package nl.rvantwisk.server.extensions

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive


inline fun <reified T> List<JsonElement>.safeGet(idx: Int, default: T): T {
    if (idx == -1 || idx >= size) {
        return when (T::class) {
            Int::class -> default
            Double::class -> default
            String::class -> default
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
    }

    val primitive = this[idx].jsonPrimitive
    return when (T::class) {
        Int::class -> primitive.int as T
        Double::class -> primitive.double as T
        String::class -> default
        else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
    }
}