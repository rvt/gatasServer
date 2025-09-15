package nl.rvantwisk.server.udp

import io.ktor.network.sockets.InetSocketAddress
import kotlin.math.min
import kotlin.time.Duration

data class TokenBucket(
    var tokens: Int,
    var lastRefillTime: Long
)

interface PerSenderRateLimiter {
  fun tryConsume(sender: InetSocketAddress): Boolean
}

interface PerUniqueRateLimiter {
  fun tryConsume(gatasId: UInt): Boolean
}

/**
 * Could this be of any help?
 * https://github.com/lpicanco/krate
 */
class TokenBucketRateLimiter(
    private val maxTokens: Int,
    private val refillInterval: Duration // time between adding 1 token
) : PerSenderRateLimiter {
    private val buckets = mutableMapOf<InetSocketAddress, TokenBucket>()
    private val refillIntervalNanos = refillInterval.inWholeNanoseconds

  @Synchronized
  override fun tryConsume(sender: InetSocketAddress): Boolean {
        val now = System.nanoTime()
        val bucket = buckets.getOrPut(sender) { TokenBucket(maxTokens, now) }

        // Calculate how many tokens to add
        val elapsed = now - bucket.lastRefillTime
        val tokensToAdd = (elapsed / refillIntervalNanos).toInt()

        if (tokensToAdd > 0) {
            bucket.tokens = min(maxTokens, bucket.tokens + tokensToAdd)
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

class UniqueIdRateLimiter(
  private val maxTokens: Int,
  private val refillInterval: Duration // time between adding 1 token
) : PerUniqueRateLimiter {
  private val buckets = mutableMapOf<UInt, TokenBucket>()
  private val refillIntervalNanos = refillInterval.inWholeNanoseconds

  @Synchronized
  override fun tryConsume(gatasId: UInt): Boolean {
    val now = System.nanoTime()
    val bucket = buckets.getOrPut(gatasId) { TokenBucket(maxTokens, now) }

    // Calculate how many tokens to add
    val elapsed = now - bucket.lastRefillTime
    val tokensToAdd = (elapsed / refillIntervalNanos).toInt()

    if (tokensToAdd > 0) {
      bucket.tokens = min(maxTokens, bucket.tokens + tokensToAdd)
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

class SimpleRateLimiter(
  private val minInterval: Duration // minimum time between requests
) : PerSenderRateLimiter {
  private val lastRequestTime = mutableMapOf<InetSocketAddress, Long>()
  private val minIntervalMillies = minInterval.inWholeMilliseconds

  @Synchronized
  override fun tryConsume(sender: InetSocketAddress): Boolean {
    val now = System.currentTimeMillis()
    val last = lastRequestTime[sender] ?: 0L

    return if (now - last >= minIntervalMillies) {
      lastRequestTime[sender] = now
      true
    } else {
      false
    }
  }
}
