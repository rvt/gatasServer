package nl.rvantwisk.gatas.server.udp

import io.ktor.network.sockets.*
import kotlin.time.Duration

data class TokenBucket(
  var tokens: Int,
  var lastRefillTime: Long
)

interface PerSenderRateLimiter {
  fun tryConsume(sender: InetSocketAddress): Boolean
}

// https://github.com/lpicanco/krate
class SimpleRateLimiter(
  minInterval: Duration,     // time between refills
  private val capacity: Int,             // max tokens per sender
  private val refillAmount: Int = 1      // tokens added per interval
) : PerSenderRateLimiter {

  private val buckets = mutableMapOf<ByteArray, TokenBucket>()
  private val minIntervalNanos = minInterval.inWholeNanoseconds

  @Synchronized
  override fun tryConsume(sender: InetSocketAddress): Boolean {
    if (sender.resolveAddress() == null) {
      return false
    }

    val now = System.nanoTime()
    val bucket = buckets.getOrPut(sender.resolveAddress()!!) {
      TokenBucket(capacity, now)
    }

    // Refill tokens based on elapsed time
    val elapsed = now - bucket.lastRefillTime
    if (elapsed >= minIntervalNanos) {
      val intervalsPassed = (elapsed / minIntervalNanos).toInt()
      val newTokens = (bucket.tokens + intervalsPassed * refillAmount)
        .coerceAtMost(capacity)
      bucket.tokens = newTokens
      bucket.lastRefillTime = now
    }

    return if (bucket.tokens > 0) {
      bucket.tokens--
      true
    } else {
      false
    }
  }
}
