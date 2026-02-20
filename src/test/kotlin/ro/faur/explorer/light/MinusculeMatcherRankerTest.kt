package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.backend.MinusculeMatcherRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

class MinusculeMatcherRankerTest : BasePlatformTestCase() {

    private lateinit var ranker: MinusculeMatcherRanker

    override fun setUp() {
        super.setUp()
        ranker = MinusculeMatcherRanker()
    }

    private fun candidate(displayName: String) = SearchCandidate(
        id = displayName,
        displayName = displayName,
        fullPath = "/tmp/$displayName",
        parentPath = "/tmp",
        type = CandidateType.DIRECTORY
    )

    fun `test blank query returns all candidates with zero score`() {
        val candidates = listOf(candidate("alpha"), candidate("beta"))
        val results = ranker.rank("", candidates, 10)
        assertEquals(2, results.size)
        assertTrue(results.all { it.score == 0.0 })
    }

    fun `test matching query returns non-empty results`() {
        val candidates = listOf(candidate("readme"), candidate("notes"), candidate("changelog"))
        val results = ranker.rank("read", candidates, 10)
        assertTrue(results.isNotEmpty())
        assertEquals("readme", results[0].candidate.displayName)
    }

    fun `test non-matching query returns empty`() {
        val candidates = listOf(candidate("hello"), candidate("world"))
        val results = ranker.rank("zzz", candidates, 10)
        assertTrue(results.isEmpty())
    }

    fun `test results have positive scores`() {
        val candidates = listOf(candidate("readme"), candidate("read-notes"))
        val results = ranker.rank("read", candidates, 10)
        assertTrue(results.isNotEmpty())
        assertTrue("All scores should be positive for a match", results.all { it.score > 0.0 })
    }

    fun `test camelcase matching works`() {
        val candidates = listOf(candidate("QuickOpenDialog"))
        val results = ranker.rank("QOD", candidates, 10)
        assertTrue(results.isNotEmpty())
    }

    fun `test limit is respected`() {
        val candidates = (1..20).map { candidate("file$it") }
        val results = ranker.rank("file", candidates, 5)
        assertTrue(results.size <= 5)
    }

    fun `test matched ranges are not empty for matching query`() {
        val candidates = listOf(candidate("readme"))
        val results = ranker.rank("read", candidates, 10)
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].matchedRanges.isNotEmpty())
    }
}
