package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.remote.security.ConnectionRateLimiter

class ConnectionRateLimiterTest {
    @Test fun `backoff does not overflow for large failure count`() {
        val limiter = ConnectionRateLimiter()
        repeat(70) { limiter.recordFailure("host") }
        val backoff = limiter.checkRateLimit("host")
        assertNotNull(backoff)
        assertTrue(backoff!!.toMillis() > 0 && backoff.toMillis() <= 16_000)
    }
    @Test fun `allows connection when no failures`() {
        val limiter = ConnectionRateLimiter()
        assertNull(limiter.checkRateLimit("fresh-host"))
    }
    @Test fun `clears backoff after success`() {
        val limiter = ConnectionRateLimiter()
        repeat(5) { limiter.recordFailure("host") }
        limiter.recordSuccess("host")
        assertNull(limiter.checkRateLimit("host"))
    }
}
