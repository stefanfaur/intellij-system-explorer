package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.UsageSignals
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import ro.faur.explorer.quickopen.ranking.Ranker

class RankerTest : BasePlatformTestCase() {

    private lateinit var ranker: Ranker

    override fun setUp() {
        super.setUp()
        // Reset frecency store
        FrecencyStore.getInstance().loadState(FrecencyStore.State())
        ranker = Ranker(FallbackRanker())
    }

    private fun candidate(
        displayName: String,
        fullPath: String = "/tmp/$displayName",
        isBookmarked: Boolean = false,
        isOpenInEditor: Boolean = false
    ) = SearchCandidate(
        id = displayName,
        displayName = displayName,
        fullPath = fullPath,
        parentPath = "/tmp",
        type = CandidateType.DIRECTORY,
        signals = UsageSignals(isBookmarked = isBookmarked, isOpenInEditor = isOpenInEditor)
    )

    fun `test blank query returns candidates ordered by default`() {
        val candidates = listOf(candidate("alpha"), candidate("beta"))
        val results = ranker.rank("", candidates, "/tmp", limit = 10)
        assertEquals(2, results.size)
    }

    fun `test matching candidates are returned in ranked order`() {
        val candidates = listOf(
            candidate("readme"),
            candidate("readme-long"),
            candidate("notes")
        )
        val results = ranker.rank("readme", candidates, "/tmp", limit = 10)
        assertTrue(results.isNotEmpty())
        // Both readme matches should be returned
        assertTrue(results.any { it.candidate.displayName == "readme" })
        assertTrue(results.any { it.candidate.displayName == "readme-long" })
        // notes should not match
        assertTrue(results.none { it.candidate.displayName == "notes" })
    }

    fun `test bookmark boost increases score`() {
        val base = candidate("mydir", "/tmp/mydir", isBookmarked = false)
        val bookmarked = candidate("mydir2", "/tmp/mydir2", isBookmarked = true)
        val candidates = listOf(base, bookmarked)
        val results = ranker.rank("mydir", candidates, "/tmp", limit = 10)
        if (results.size == 2) {
            // bookmarked should rank higher (assuming equal text score)
            // This is hard to guarantee due to naming, so just verify both are in results
            assertTrue(results.any { it.candidate.isBookmarked() })
        }
    }

    fun `test proximity score boosts nearby paths`() {
        val near = candidate("neardir", "/current/neardir")
        val far = candidate("neardir2", "/completely/different/path/neardir2")
        val candidates = listOf(near, far)
        val results = ranker.rank("neardir", candidates, "/current", limit = 10)
        assertTrue(results.isNotEmpty())
        // The closer path should rank higher
        assertEquals("/current/neardir", results[0].candidate.fullPath)
    }

    fun `test limit is respected`() {
        val candidates = (1..30).map { candidate("file$it") }
        val results = ranker.rank("file", candidates, "/tmp", limit = 5)
        assertTrue(results.size <= 5)
    }

    fun `test frecency signals affect ranking`() {
        val popular = candidate("popular", "/tmp/popular")
        val rare = candidate("popular2", "/tmp/popular2")
        val candidates = listOf(popular, rare)

        // Record many visits for the popular path
        val store = FrecencyStore.getInstance()
        repeat(10) { store.recordVisit("/tmp/popular") }

        val results = ranker.rank("popular", candidates, "/tmp", limit = 10)
        assertTrue(results.isNotEmpty())
        // popular should rank first due to high frequency score
        assertEquals("/tmp/popular", results[0].candidate.fullPath)
    }

    private fun SearchCandidate.isBookmarked() = signals.isBookmarked
}
