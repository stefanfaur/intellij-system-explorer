package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

class FallbackRankerTest {

    private val ranker = FallbackRanker()

    private fun candidate(displayName: String, fullPath: String = "/tmp/$displayName") = SearchCandidate(
        id = displayName,
        displayName = displayName,
        fullPath = fullPath,
        parentPath = "/tmp",
        type = CandidateType.DIRECTORY
    )

    @Test
    fun `blank query returns all candidates with zero score`() {
        val candidates = listOf(candidate("alpha"), candidate("beta"), candidate("gamma"))
        val results = ranker.rank("", candidates, 10)
        assertEquals(3, results.size)
        assertTrue(results.all { it.score == 0.0 })
    }

    @Test
    fun `exact match scores higher than partial match`() {
        val candidates = listOf(
            candidate("readme"),
            candidate("readme-long-extra-stuff")
        )
        val results = ranker.rank("readme", candidates, 10)
        assertEquals(2, results.size)
        // Exact match should be first (it gets the exact bonus)
        assertEquals("readme", results[0].candidate.displayName)
    }

    @Test
    fun `prefix match scores higher than substring match`() {
        val candidates = listOf(
            candidate("foobar"),   // query "foo" is a prefix
            candidate("xfoobar")   // query "foo" is a substring, not prefix
        )
        val results = ranker.rank("foo", candidates, 10)
        // Both should match; prefix should rank higher
        assertTrue(results.isNotEmpty())
        assertEquals("foobar", results[0].candidate.displayName)
    }

    @Test
    fun `query longer than candidate is excluded`() {
        // Smith-Waterman excludes candidates where query is longer than text (m > n check)
        val candidates = listOf(candidate("ab"))  // length 2
        val results = ranker.rank("abcdef", candidates, 10) // length 6 > 2
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matched ranges are returned for matches`() {
        val candidates = listOf(candidate("readme"))
        val results = ranker.rank("read", candidates, 10)
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].matchedRanges.isNotEmpty())
    }

    @Test
    fun `limit is respected`() {
        val candidates = (1..20).map { candidate("doc$it") }
        val results = ranker.rank("doc", candidates, 5)
        assertTrue(results.size <= 5)
    }

    @Test
    fun `case insensitive matching works`() {
        val candidates = listOf(candidate("README"), candidate("Notes"))
        val results = ranker.rank("readme", candidates, 10)
        assertTrue(results.isNotEmpty())
        assertEquals("README", results[0].candidate.displayName)
    }

    @Test
    fun `matched ranges are contiguous where possible`() {
        val candidates = listOf(candidate("foobar"))
        val results = ranker.rank("foo", candidates, 10)
        assertTrue(results.isNotEmpty())
        val ranges = results[0].matchedRanges
        assertTrue(ranges.isNotEmpty())
        // The first range should start at 0 (prefix "foo")
        assertEquals(0, ranges[0].first)
        assertEquals(2, ranges[0].last)
    }
}
