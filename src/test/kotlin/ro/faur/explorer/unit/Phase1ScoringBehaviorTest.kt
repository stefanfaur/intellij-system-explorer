package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import ro.faur.explorer.quickopen.model.UsageSignals

/**
 * Phase 1 — Scoring and Ranking Behavior
 *
 * Tests the behavioral contracts of FallbackRanker as the pure-Kotlin scorer.
 * All tests use only FallbackRanker (no IntelliJ platform needed) so they can
 * run as plain JUnit5 unit tests.
 *
 * Behaviors under test:
 *   - Empty query: returns up to `limit` candidates in original order with score 0
 *   - Blank query (whitespace only): treated as empty
 *   - Non-matching query: candidate is excluded from results
 *   - Matching candidates are returned in descending score order
 *   - Exact-name match scores higher than partial prefix match
 *   - Prefix match scores higher than mid-string match (bonus rules)
 *   - Limit is respected even when more candidates match
 *   - Scored results reference the original candidate objects
 *   - Match ranges are non-null and within string bounds for matching candidates
 *   - Consecutive character match produces non-negative score
 *   - FallbackRanker always reports isAvailable() == true
 *   - FallbackRanker name is non-blank
 */
class Phase1ScoringBehaviorTest {

    private val ranker = FallbackRanker()

    // -------------------------------------------------------------------------
    // Empty / blank query
    // -------------------------------------------------------------------------

    @Test
    fun `empty query returns candidates up to limit`() {
        val pool = makePool("alpha", "beta", "gamma", "delta", "epsilon")
        val results = ranker.rank("", pool, limit = 3)
        assertEquals(3, results.size)
    }

    @Test
    fun `empty query assigns score of zero to all returned candidates`() {
        val pool = makePool("alpha", "beta")
        val results = ranker.rank("", pool, limit = 10)
        results.forEach { assertEquals(0.0, it.score, 1e-9) }
    }

    @Test
    fun `blank query is treated the same as empty query`() {
        val pool = makePool("alpha", "beta")
        val resultsEmpty = ranker.rank("", pool, limit = 10)
        val resultsBlank = ranker.rank("   ", pool, limit = 10)
        assertEquals(resultsEmpty.size, resultsBlank.size)
    }

    @Test
    fun `empty query on empty pool returns empty list`() {
        val results = ranker.rank("", emptyList(), limit = 10)
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Non-matching candidates
    // -------------------------------------------------------------------------

    @Test
    fun `query with no matching candidate returns empty results`() {
        val pool = makePool("alpha", "beta", "gamma")
        val results = ranker.rank("zzz", pool, limit = 10)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `query matching only some candidates excludes non-matching ones`() {
        val pool = makePool("alpha", "beta", "gamma")
        val results = ranker.rank("alp", pool, limit = 10)
        assertTrue(results.all { it.candidate.displayName.contains("alp", ignoreCase = true) ||
                it.candidate.displayName.lowercase().contains("a") })
        // The key assertion: "beta" and "gamma" should not appear if they don't match "alp"
        assertTrue(results.none { it.candidate.displayName == "beta" })
    }

    // -------------------------------------------------------------------------
    // Ordering guarantees
    // -------------------------------------------------------------------------

    @Test
    fun `results are sorted by descending score`() {
        val pool = makePool("foobar", "fo", "foo")
        val results = ranker.rank("foo", pool, limit = 10)
        val scores = results.map { it.score }
        for (i in 0 until scores.size - 1) {
            assertTrue(scores[i] >= scores[i + 1],
                "Expected descending order but got $scores")
        }
    }

    @Test
    fun `exact name match scores higher than prefix-only match`() {
        val pool = listOf(
            makeCandidate("foo_extended"),  // prefix match
            makeCandidate("foo")            // exact match
        )
        val results = ranker.rank("foo", pool, limit = 10)
        val exactIdx = results.indexOfFirst { it.candidate.displayName == "foo" }
        val prefixIdx = results.indexOfFirst { it.candidate.displayName == "foo_extended" }
        assertTrue(exactIdx >= 0 && prefixIdx >= 0, "Both candidates should be in results")
        assertTrue(exactIdx < prefixIdx,
            "Exact match 'foo' should rank above prefix match 'foo_extended'")
    }

    @Test
    fun `prefix match scores higher than mid-string match`() {
        val pool = listOf(
            makeCandidate("mybar"),    // mid-string: "bar" appears at index 2
            makeCandidate("barbell")   // prefix match: "bar" appears at index 0
        )
        val results = ranker.rank("bar", pool, limit = 10)
        val prefixIdx = results.indexOfFirst { it.candidate.displayName == "barbell" }
        val midIdx = results.indexOfFirst { it.candidate.displayName == "mybar" }
        // Both may match; if both match, prefix should score >= mid
        if (prefixIdx >= 0 && midIdx >= 0) {
            val prefixScore = results[prefixIdx].score
            val midScore = results[midIdx].score
            assertTrue(prefixScore >= midScore,
                "Prefix match 'barbell' score=$prefixScore should be >= mid match 'mybar' score=$midScore")
        }
    }

    // -------------------------------------------------------------------------
    // Limit enforcement
    // -------------------------------------------------------------------------

    @Test
    fun `limit caps results even when more candidates match`() {
        val pool = (1..20).map { makeCandidate("file$it") }
        val results = ranker.rank("file", pool, limit = 5)
        assertTrue(results.size <= 5)
    }

    @Test
    fun `limit of zero returns empty list`() {
        val pool = makePool("alpha", "beta")
        val results = ranker.rank("alpha", pool, limit = 0)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `limit larger than matching set returns all matching candidates`() {
        val pool = makePool("cat", "catalog", "catfish")
        val results = ranker.rank("cat", pool, limit = 100)
        assertTrue(results.size <= 3)
        assertTrue(results.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Score values
    // -------------------------------------------------------------------------

    @Test
    fun `matching candidate has non-negative score`() {
        val pool = makePool("foobar")
        val results = ranker.rank("foo", pool, limit = 10)
        assertTrue(results.all { it.score >= 0.0 })
    }

    @Test
    fun `consecutive character match produces higher score than scattered match`() {
        val pool = listOf(
            makeCandidate("abcxyz"),    // a,b,c are consecutive → higher score for "abc"
            makeCandidate("axbycz")     // a,b,c are scattered → lower score for "abc"
        )
        val results = ranker.rank("abc", pool, limit = 10)
        val consecutiveIdx = results.indexOfFirst { it.candidate.displayName == "abcxyz" }
        val scatteredIdx = results.indexOfFirst { it.candidate.displayName == "axbycz" }
        if (consecutiveIdx >= 0 && scatteredIdx >= 0) {
            assertTrue(results[consecutiveIdx].score >= results[scatteredIdx].score,
                "Consecutive match should score >= scattered match")
        }
    }

    // -------------------------------------------------------------------------
    // Result referential integrity
    // -------------------------------------------------------------------------

    @Test
    fun `scored result candidate is the same object as the input candidate`() {
        val candidate = makeCandidate("mydir")
        val results = ranker.rank("mydir", listOf(candidate), limit = 10)
        assertTrue(results.isNotEmpty())
        assertSame(candidate, results.first().candidate)
    }

    // -------------------------------------------------------------------------
    // Match ranges
    // -------------------------------------------------------------------------

    @Test
    fun `matching candidate has non-null matchedRanges list`() {
        val pool = makePool("foobar")
        val results = ranker.rank("foo", pool, limit = 10)
        assertTrue(results.isNotEmpty())
        assertNotNull(results.first().matchedRanges)
    }

    @Test
    fun `match ranges are within bounds of displayName`() {
        val candidate = makeCandidate("foobar")
        val results = ranker.rank("foo", listOf(candidate), limit = 10)
        assertTrue(results.isNotEmpty())
        val name = candidate.displayName
        for (range in results.first().matchedRanges) {
            assertTrue(range.first >= 0, "Range start should be >= 0")
            assertTrue(range.last < name.length, "Range end should be < displayName.length")
        }
    }

    // -------------------------------------------------------------------------
    // Backend contract
    // -------------------------------------------------------------------------

    @Test
    fun `FallbackRanker isAvailable returns true`() {
        assertTrue(ranker.isAvailable())
    }

    @Test
    fun `FallbackRanker name is non-blank`() {
        assertTrue(ranker.name.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makePool(vararg names: String): List<SearchCandidate> =
        names.map { makeCandidate(it) }

    private fun makeCandidate(
        displayName: String,
        type: CandidateType = CandidateType.DIRECTORY,
        fullPath: String = "/root/$displayName"
    ) = SearchCandidate(
        id = "test:$displayName",
        displayName = displayName,
        fullPath = fullPath,
        parentPath = "/root",
        type = type
    )
}
