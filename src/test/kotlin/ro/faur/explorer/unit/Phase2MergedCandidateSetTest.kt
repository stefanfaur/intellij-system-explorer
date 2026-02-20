package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.UsageSignals
import ro.faur.explorer.quickopen.ranking.FrecencyStore

/**
 * Phase 2 — Merged Candidate Set Behavior
 *
 * Tests the behavior when static candidates (bookmarks, recent, editors, actions)
 * are merged with dynamic filesystem candidates (from CandidatePool).
 *
 * The plan specifies:
 *   - performSearch merges: candidates + candidatePool.getCandidates()
 *   - A candidate that appears in both static and dynamic lists should not be
 *     de-duplicated automatically (de-dup is the caller's responsibility)
 *   - All candidate types participate in the same ranked list
 *   - Bookmarked candidates with isBookmarked=true receive a boost in Ranker output
 *   - Open-editor candidates with isOpenInEditor=true receive a boost
 *   - The merged list is ranked by Ranker, respecting the 50-result limit
 *
 * Uses FallbackRanker and a local FrecencyStore to verify ordering without
 * requiring the IntelliJ platform.
 */
class Phase2MergedCandidateSetTest {

    private val ranker = FallbackRanker()

    // -------------------------------------------------------------------------
    // Merged pool composition
    // -------------------------------------------------------------------------

    @Test
    fun `merged pool contains candidates from both static and dynamic lists`() {
        val static = listOf(makeCandidate("bookmarked", CandidateType.BOOKMARK))
        val dynamic = listOf(makeCandidate("filesystem", CandidateType.FILE))
        val merged = static + dynamic
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.type == CandidateType.BOOKMARK })
        assertTrue(merged.any { it.type == CandidateType.FILE })
    }

    @Test
    fun `merged pool with empty dynamic list equals static list`() {
        val static = listOf(makeCandidate("recent", CandidateType.RECENT))
        val dynamic = emptyList<SearchCandidate>()
        val merged = static + dynamic
        assertEquals(static, merged)
    }

    @Test
    fun `merged pool with empty static list equals dynamic list`() {
        val static = emptyList<SearchCandidate>()
        val dynamic = listOf(makeCandidate("file", CandidateType.FILE))
        val merged = static + dynamic
        assertEquals(dynamic, merged)
    }

    @Test
    fun `all candidate types can coexist in merged ranked results`() {
        val pool = listOf(
            makeCandidate("docs", CandidateType.DIRECTORY),
            makeCandidate("readme", CandidateType.FILE),
            makeCandidate("bookmark", CandidateType.BOOKMARK),
            makeCandidate("recent", CandidateType.RECENT),
            makeCandidate("editor", CandidateType.OPEN_EDITOR),
            makeCandidate("action", CandidateType.ACTION)
        )
        val results = ranker.rank("", pool, limit = 10)
        // All six types should make it into results (empty query returns in insertion order)
        assertEquals(6, results.size)
    }

    // -------------------------------------------------------------------------
    // Bookmarked boost semantics (via UsageSignals)
    // -------------------------------------------------------------------------

    @Test
    fun `bookmarked candidate has isBookmarked true in its signals`() {
        val candidate = makeCandidate("mydir", CandidateType.BOOKMARK, isBookmarked = true)
        assertTrue(candidate.signals.isBookmarked)
    }

    @Test
    fun `non-bookmarked candidate has isBookmarked false in its signals`() {
        val candidate = makeCandidate("mydir", CandidateType.DIRECTORY, isBookmarked = false)
        assertFalse(candidate.signals.isBookmarked)
    }

    @Test
    fun `openInEditor candidate has isOpenInEditor true in its signals`() {
        val candidate = makeCandidate("myfile", CandidateType.OPEN_EDITOR, isOpenInEditor = true)
        assertTrue(candidate.signals.isOpenInEditor)
    }

    // -------------------------------------------------------------------------
    // Frecency signals flow into candidate display
    // -------------------------------------------------------------------------

    @Test
    fun `FrecencyStore signals can be injected into candidate via copy`() {
        val store = FrecencyStore()
        val path = "/home/user/Projects"
        repeat(5) { store.recordVisit(path) }
        store.setBookmarked(path, true)

        val baseCandidate = makeCandidate("Projects", CandidateType.DIRECTORY, fullPath = path)
        val storeSignals = store.getSignals(path)
        val enrichedCandidate = baseCandidate.copy(
            signals = storeSignals.copy(isBookmarked = true)
        )

        assertTrue(enrichedCandidate.signals.isBookmarked)
        assertEquals(5, enrichedCandidate.signals.useCount)
    }

    @Test
    fun `candidate with higher useCount has larger frequencyScore than one with lower useCount`() {
        val store = FrecencyStore()
        val frequentPath = "/home/user/Frequent"
        val infrequentPath = "/home/user/Infrequent"
        repeat(10) { store.recordVisit(frequentPath) }
        store.recordVisit(infrequentPath)
        assertTrue(store.frequencyScore(frequentPath) > store.frequencyScore(infrequentPath))
    }

    // -------------------------------------------------------------------------
    // Merged pool ranking respects limit
    // -------------------------------------------------------------------------

    @Test
    fun `ranker limits merged pool output to the specified limit`() {
        val pool = (1..30).map { makeCandidate("item$it", CandidateType.FILE) }
        val results = ranker.rank("item", pool, limit = 10)
        assertTrue(results.size <= 10,
            "Expected at most 10 results but got ${results.size}")
    }

    @Test
    fun `ranker on merged pool returns non-empty results when query matches`() {
        val static = listOf(makeCandidate("BookmarkDir", CandidateType.BOOKMARK))
        val dynamic = listOf(makeCandidate("BookmarkFile", CandidateType.FILE))
        val merged = static + dynamic
        val results = ranker.rank("Bookmark", merged, limit = 50)
        assertTrue(results.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // No automatic de-duplication
    // -------------------------------------------------------------------------

    @Test
    fun `duplicate candidates in merged pool both appear in results`() {
        val path = "/home/user/Projects"
        val asBookmark = SearchCandidate(
            id = "bm:$path", displayName = "Projects", fullPath = path,
            parentPath = "/home/user", type = CandidateType.BOOKMARK
        )
        val asFilesystem = SearchCandidate(
            id = "fs:$path", displayName = "Projects", fullPath = path,
            parentPath = "/home/user", type = CandidateType.DIRECTORY
        )
        val merged = listOf(asBookmark, asFilesystem)
        val results = ranker.rank("", merged, limit = 10)
        // Both should be present because de-dup is not the ranker's responsibility
        assertEquals(2, results.size)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeCandidate(
        displayName: String,
        type: CandidateType,
        fullPath: String = "/root/$displayName",
        isBookmarked: Boolean = false,
        isOpenInEditor: Boolean = false
    ) = SearchCandidate(
        id = "${type.name.lowercase()}:$fullPath",
        displayName = displayName,
        fullPath = fullPath,
        parentPath = fullPath.substringBeforeLast('/'),
        type = type,
        signals = UsageSignals(isBookmarked = isBookmarked, isOpenInEditor = isOpenInEditor)
    )
}
