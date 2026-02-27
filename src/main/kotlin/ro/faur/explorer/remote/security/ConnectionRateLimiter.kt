package ro.faur.explorer.remote.security

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-host connection failure history and enforces exponential backoff.
 *
 * Backoff schedule (capped at 16 s):
 *   1 failure  ->  2 s
 *   2 failures ->  4 s
 *   3 failures ->  8 s
 *   4+ failures-> 16 s
 */
class ConnectionRateLimiter {

    private data class HostState(
        var failureCount: Int = 0,
        val lastFailureTime: Instant? = null
    )

    private val hostStates = ConcurrentHashMap<String, HostState>()

    companion object {
        private const val BASE_BACKOFF_MS = 1000L
        private val MAX_BACKOFF: Duration = Duration.ofSeconds(16)
    }

    /**
     * Returns the remaining backoff duration for [host], or null if no delay is needed.
     * A null result means the caller may proceed with the connection immediately.
     */
    fun checkRateLimit(host: String): Duration? {
        val state = hostStates[host] ?: return null
        if (state.failureCount == 0 || state.lastFailureTime == null) return null

        val backoff = backoffForCount(state.failureCount)
        val elapsed = Duration.between(state.lastFailureTime, Instant.now())
        val remaining = backoff.minus(elapsed)

        return if (remaining.isPositive) remaining else null
    }

    /**
     * Records a connection failure for [host], incrementing the failure counter and
     * updating the timestamp used by [checkRateLimit].
     */
    fun recordFailure(host: String) {
        hostStates.compute(host) { _, existing ->
            val current = existing ?: HostState()
            current.copy(
                failureCount = (current.failureCount + 1).coerceAtMost(60),
                lastFailureTime = Instant.now()
            )
        }
    }

    /**
     * Records a successful connection for [host], resetting its backoff state entirely.
     */
    fun recordSuccess(host: String) {
        hostStates.remove(host)
    }

    /**
     * Returns the full backoff [Duration] for [host] based on its current failure count,
     * regardless of how much time has already elapsed. Returns [Duration.ZERO] if the
     * host has no recorded failures.
     */
    fun getBackoffDuration(host: String): Duration {
        val state = hostStates[host] ?: return Duration.ZERO
        return backoffForCount(state.failureCount)
    }

    // Internal: compute capped exponential backoff from failure count.
    private fun backoffForCount(failureCount: Int): Duration {
        if (failureCount <= 0) return Duration.ZERO
        val safeCap = failureCount.coerceAtMost(30)  // prevent Long overflow at 1L shl 63
        val rawMs = BASE_BACKOFF_MS * (1L shl safeCap)
        return if (rawMs >= MAX_BACKOFF.toMillis()) MAX_BACKOFF
               else Duration.ofMillis(rawMs)
    }
}
