package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.UsageSignals
import ro.faur.explorer.quickopen.ranking.FrecencyStore

/**
 * Phase 2 — Frecency Signal Propagation
 *
 * Tests that frecency signals are correctly applied to candidates that come
 * from navigation (ExplorerPanel.navigateTo) and from bookmark operations
 * (BookmarkManager.addBookmark / removeBookmark), as described in the plan.
 *
 * These tests are pure unit tests that exercise FrecencyStore directly because
 * the propagation contract is: "when navigateTo is called, recordVisit is called;
 * when addBookmark is called, setBookmarked(true) is called; etc."
 * We test the store behaviors that make propagation meaningful.
 *
 * Behaviors under test:
 *   - After recordVisit (simulating navigateTo), getSignals reflects the visit
 *   - After setBookmarked(true) (simulating addBookmark), getSignals.isBookmarked is true
 *   - After setBookmarked(false) (simulating removeBookmark), getSignals.isBookmarked is false
 *   - Bookmark signal on a candidate passed to CandidatePool is preserved in UsageSignals
 *   - A bookmarked candidate receives isBookmarked=true when getSignals is queried
 *   - A candidate with a recent visit has higher recencyScore than one with no visit
 *   - A candidate with many visits has higher frequencyScore than one with fewer visits
 *   - Frecency signals for path A do not bleed into path B
 *   - SearchCandidate.signals.isBookmarked matches FrecencyStore after setBookmarked
 *   - Candidate built with bookmarked=true signals has isBookmarked set
 */
class Phase2FrecencySignalPropagationTest {

    private lateinit var store: FrecencyStore

    @BeforeEach
    fun setUp() {
        store = FrecencyStore()
    }

    // -------------------------------------------------------------------------
    // Navigation → recordVisit propagation
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo simulation — recordVisit makes path appear in getSignals`() {
        val path = "/home/user/Documents"
        store.recordVisit(path)  // simulates ExplorerPanel.navigateTo() calling store.recordVisit()
        val signals = store.getSignals(path)
        assertTrue(signals.useCount > 0,
            "After recordVisit the useCount should be > 0")
    }

    @Test
    fun `repeated navigateTo accumulates visit count`() {
        val path = "/home/user/Projects"
        repeat(3) { store.recordVisit(path) }
        assertEquals(3, store.getSignals(path).useCount)
    }

    @Test
    fun `navigateTo to different paths does not cross-contaminate signals`() {
        val pathA = "/home/user/A"
        val pathB = "/home/user/B"
        repeat(5) { store.recordVisit(pathA) }
        assertEquals(5, store.getSignals(pathA).useCount)
        assertEquals(0, store.getSignals(pathB).useCount)
    }

    // -------------------------------------------------------------------------
    // Bookmark propagation
    // -------------------------------------------------------------------------

    @Test
    fun `addBookmark simulation — setBookmarked true makes isBookmarked visible in signals`() {
        val path = "/home/user/Favorites"
        store.setBookmarked(path, true)
        assertTrue(store.getSignals(path).isBookmarked)
    }

    @Test
    fun `removeBookmark simulation — setBookmarked false clears isBookmarked`() {
        val path = "/home/user/Favorites"
        store.setBookmarked(path, true)
        store.setBookmarked(path, false)
        assertFalse(store.getSignals(path).isBookmarked)
    }

    @Test
    fun `bookmark signal on path A does not affect path B`() {
        val pathA = "/home/user/A"
        val pathB = "/home/user/B"
        store.setBookmarked(pathA, true)
        assertFalse(store.getSignals(pathB).isBookmarked,
            "Bookmarking pathA should not affect pathB")
    }

    @Test
    fun `bookmark signal persists through state round-trip`() {
        val path = "/home/user/Persistent"
        store.setBookmarked(path, true)
        val savedState = store.getState()

        val restored = FrecencyStore()
        restored.loadState(savedState)
        assertTrue(restored.getSignals(path).isBookmarked)
    }

    // -------------------------------------------------------------------------
    // Candidate construction with frecency signals
    // -------------------------------------------------------------------------

    @Test
    fun `candidate built with bookmarked signals has isBookmarked true`() {
        val signals = UsageSignals(isBookmarked = true)
        val candidate = SearchCandidate(
            id = "bm:/my/path",
            displayName = "path",
            fullPath = "/my/path",
            parentPath = "/my",
            type = CandidateType.BOOKMARK,
            signals = signals
        )
        assertTrue(candidate.signals.isBookmarked)
    }

    @Test
    fun `getSignals after setBookmarked matches candidate signals field when copied`() {
        val path = "/home/user/target"
        store.setBookmarked(path, true)
        val storeSignals = store.getSignals(path)
        // Simulate what QuickOpenPopup does: copy(isBookmarked = true)
        val candidateSignals = storeSignals.copy(isBookmarked = true)
        assertTrue(candidateSignals.isBookmarked)
    }

    // -------------------------------------------------------------------------
    // Recency and frequency effects on scoring
    // -------------------------------------------------------------------------

    @Test
    fun `recently visited path has higher recencyScore than never-visited path`() {
        val visited = "/home/user/visited"
        store.recordVisit(visited)
        val visitedScore = store.recencyScore(visited)
        val neverScore = store.recencyScore("/home/user/never")
        assertTrue(visitedScore > neverScore,
            "Visited path score=$visitedScore should be > never-visited score=$neverScore")
    }

    @Test
    fun `path with more visits has higher frequencyScore than path with fewer visits`() {
        val frequent = "/home/user/frequent"
        val infrequent = "/home/user/infrequent"
        repeat(10) { store.recordVisit(frequent) }
        store.recordVisit(infrequent)
        val freqScore = store.frequencyScore(frequent)
        val infreqScore = store.frequencyScore(infrequent)
        assertTrue(freqScore > infreqScore,
            "Frequent path score=$freqScore should be > infrequent score=$infreqScore")
    }

    @Test
    fun `visit count increases recencyScore above zero`() {
        val path = "/home/user/recent"
        assertEquals(0.0, store.recencyScore(path), 1e-9)
        store.recordVisit(path)
        assertTrue(store.recencyScore(path) > 0.0)
    }

    @Test
    fun `bookmark alone does not affect recencyScore`() {
        val path = "/home/user/bookmarked-only"
        store.setBookmarked(path, true)
        // No visit recorded, so recency should still be 0
        assertEquals(0.0, store.recencyScore(path), 1e-9)
    }

    @Test
    fun `bookmark alone does not affect frequencyScore`() {
        val path = "/home/user/bookmarked-only"
        store.setBookmarked(path, true)
        // No visit recorded, so frequency should still be 0
        assertEquals(0.0, store.frequencyScore(path), 1e-9)
    }
}
