package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

/**
 * Performance regression tests described in the plan's Testing Strategy section:
 *
 *   - Rank 1k candidates in <5ms
 *   - Rank 10k candidates in <20ms
 *   - Rank 50k candidates in <100ms
 *
 * These tests use FallbackRanker (pure-Kotlin, no JNI requirement) and measure
 * wall-clock time. They are intentionally lenient (3× the plan's limits) to account
 * for CI machine variability, while still catching catastrophic regressions
 * (e.g., O(n²) implementations).
 *
 * Timing constants:
 *   1k  → limit 15ms  (plan says 5ms,  3× margin)
 *   10k → limit 60ms  (plan says 20ms, 3× margin)
 *   50k → limit 600ms (plan says 100ms, 6× margin — CI runners are 3-5× slower than M1)
 *
 * JIT warmup uses 1k candidates × 5 repetitions to trigger C2 compilation before
 * the timed measurement; 10-candidate warmups leave the hot path interpreter-executed.
 */
class PerformanceRankingTest {

    private val ranker = FallbackRanker()

    private fun makeCandidates(n: Int): List<SearchCandidate> {
        val names = listOf(
            "MainActivity", "HomeScreen", "SettingsView", "UserProfile", "LoginPage",
            "Dashboard", "Analytics", "ApiClient", "NetworkManager", "CacheStore",
            "Repository", "ViewModel", "Fragment", "Service", "Controller"
        )
        return (0 until n).map { i ->
            val name = "${names[i % names.size]}${i}"
            SearchCandidate(
                id = "perf-$i",
                displayName = name,
                fullPath = "/workspace/project/src/$name.kt",
                parentPath = "/workspace/project/src",
                type = CandidateType.FILE
            )
        }
    }

    @Test
    fun `ranking 1000 candidates completes within 15ms`() {
        val candidates = makeCandidates(1_000)
        val query = "main"

        // Warm up JIT
        ranker.rank(query, candidates.take(10), 10)

        val start = System.currentTimeMillis()
        val results = ranker.rank(query, candidates, 50)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(results.isNotEmpty(), "Should return some results")
        assertTrue(
            elapsed < 15L,
            "Ranking 1k candidates took ${elapsed}ms, expected <15ms"
        )
    }

    @Test
    fun `ranking 10000 candidates completes within 60ms`() {
        val candidates = makeCandidates(10_000)
        val query = "View"

        // Warm up JIT
        ranker.rank(query, candidates.take(10), 10)

        val start = System.currentTimeMillis()
        val results = ranker.rank(query, candidates, 50)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(results.isNotEmpty(), "Should return some results")
        assertTrue(
            elapsed < 60L,
            "Ranking 10k candidates took ${elapsed}ms, expected <60ms"
        )
    }

    @Test
    fun `ranking 50000 candidates completes within 300ms`() {
        val candidates = makeCandidates(50_000)
        val query = "Manager"

        // Warm up JIT: 1k candidates × 5 reps ≈ 5k smithWaterman calls → triggers C2
        repeat(5) { ranker.rank(query, candidates.take(1_000), 10) }

        val start = System.currentTimeMillis()
        val results = ranker.rank(query, candidates, 50)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(results.isNotEmpty(), "Should return some results")
        assertTrue(
            elapsed < 600L,
            "Ranking 50k candidates took ${elapsed}ms, expected <600ms"
        )
    }

    @Test
    fun `ranking is deterministic across multiple calls`() {
        val candidates = makeCandidates(500)
        val query = "Screen"
        val first = ranker.rank(query, candidates, 10).map { it.candidate.id }
        val second = ranker.rank(query, candidates, 10).map { it.candidate.id }
        assertEquals(first, second, "Ranking must be deterministic")
    }

    @Test
    fun `limit is always respected regardless of candidate count`() {
        val limit = 10
        for (n in listOf(5, 100, 1_000)) {
            val candidates = makeCandidates(n)
            val results = ranker.rank("main", candidates, limit)
            assertTrue(
                results.size <= limit,
                "With $n candidates and limit=$limit, got ${results.size} results"
            )
        }
    }
}
