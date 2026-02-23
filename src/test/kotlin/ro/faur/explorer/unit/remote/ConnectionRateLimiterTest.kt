package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.security.ConnectionRateLimiter
import java.time.Duration

class ConnectionRateLimiterTest {

    private lateinit var limiter: ConnectionRateLimiter

    @BeforeEach
    fun setup() {
        limiter = ConnectionRateLimiter()
    }

    @Test
    fun `no delay for first attempt`() {
        val delay = limiter.checkRateLimit("host.example.com")
        assertNull(delay)
    }

    @Test
    fun `records failure and returns delay on next check`() {
        limiter.recordFailure("host.example.com")
        val delay = limiter.checkRateLimit("host.example.com")
        assertNotNull(delay)
        assertTrue(delay!!.toMillis() > 0)
    }

    @Test
    fun `backoff increases exponentially`() {
        limiter.recordFailure("host")
        val delay1 = limiter.getBackoffDuration("host")
        limiter.recordFailure("host")
        val delay2 = limiter.getBackoffDuration("host")
        limiter.recordFailure("host")
        val delay3 = limiter.getBackoffDuration("host")
        assertTrue(delay2 > delay1)
        assertTrue(delay3 > delay2)
    }

    @Test
    fun `backoff caps at 16 seconds`() {
        repeat(10) { limiter.recordFailure("host") }
        val delay = limiter.getBackoffDuration("host")
        assertTrue(delay <= Duration.ofSeconds(16))
    }

    @Test
    fun `successful connection resets backoff`() {
        limiter.recordFailure("host")
        limiter.recordFailure("host")
        limiter.recordFailure("host")
        limiter.recordSuccess("host")
        assertNull(limiter.checkRateLimit("host"))
    }

    @Test
    fun `different hosts have independent backoffs`() {
        limiter.recordFailure("hostA")
        limiter.recordFailure("hostA")
        limiter.recordFailure("hostA")
        assertNull(limiter.checkRateLimit("hostB"))
        assertNotNull(limiter.checkRateLimit("hostA"))
    }

    @Test
    fun `thread-safe concurrent access`() {
        val threads = (1..10).map {
            Thread {
                repeat(50) {
                    limiter.recordFailure("host")
                    limiter.checkRateLimit("host")
                    limiter.recordSuccess("host")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
    }
}
