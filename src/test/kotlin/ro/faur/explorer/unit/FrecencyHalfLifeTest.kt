package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

class FrecencyHalfLifeTest {

    private fun recencyScore(hoursAgo: Double, halfLifeHours: Double): Double =
        exp(-hoursAgo * ln(2.0) / halfLifeHours)

    @Test fun `score is 1 when just used`() {
        assertEquals(1.0, recencyScore(0.0, 24.0), 1e-9)
    }

    @Test fun `score is 0-5 at half-life`() {
        val score = recencyScore(24.0, 24.0)
        assertTrue(abs(score - 0.5) < 1e-9, "Expected ~0.5 at half-life, got $score")
    }

    @Test fun `shorter half-life decays faster`() {
        val fast = recencyScore(12.0, 6.0)
        val slow = recencyScore(12.0, 48.0)
        assertTrue(fast < slow, "Shorter half-life should decay faster")
    }
}
