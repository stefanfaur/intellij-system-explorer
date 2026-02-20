package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.ranking.FrecencyStore

/**
 * Phase 1 — FrecencyStore Behavior
 *
 * Tests the pure-logic behaviors of FrecencyStore that can be exercised
 * without the IntelliJ application context by constructing the store directly
 * (bypassing ApplicationManager.getService()).
 *
 * Behaviors under test:
 *   - recordVisit increments useCount and updates lastUsedMs
 *   - getSignals returns zero-valued UsageSignals for unknown path
 *   - getSignals reflects recorded visits
 *   - multiple visits accumulate use count
 *   - setBookmarked(true) is reflected by getSignals
 *   - setBookmarked(false) clears bookmark flag
 *   - recencyScore for a just-visited path is close to 1.0 (< 1ms old)
 *   - recencyScore for unknown path is 0.0
 *   - frequencyScore for unknown path is 0.0
 *   - frequencyScore rises with more visits
 *   - frequencyScore is normalized to [0, 1]
 *   - maxUseCount is tracked across all paths
 *   - branch is stored by recordVisit when provided
 *   - branch stored as blank is returned as null in UsageSignals
 *   - State round-trip via getState/loadState preserves entries
 *   - Loading state replaces all previous entries
 */
class Phase1FrecencyStoreBehaviorTest {

    private lateinit var store: FrecencyStore

    @BeforeEach
    fun setUp() {
        // Construct directly without IntelliJ services
        store = FrecencyStore()
    }

    // -------------------------------------------------------------------------
    // recordVisit basics
    // -------------------------------------------------------------------------

    @Test
    fun `getSignals returns default UsageSignals for unknown path`() {
        val signals = store.getSignals("/nonexistent/path")
        assertEquals(0L, signals.lastUsedMs)
        assertEquals(0, signals.useCount)
        assertFalse(signals.isBookmarked)
        assertNull(signals.currentBranch)
    }

    @Test
    fun `recordVisit sets useCount to one after first visit`() {
        store.recordVisit("/foo/bar")
        val signals = store.getSignals("/foo/bar")
        assertEquals(1, signals.useCount)
    }

    @Test
    fun `recordVisit updates lastUsedMs to a recent timestamp`() {
        val before = System.currentTimeMillis()
        store.recordVisit("/foo/bar")
        val after = System.currentTimeMillis()
        val signals = store.getSignals("/foo/bar")
        assertTrue(signals.lastUsedMs in before..after,
            "lastUsedMs=${signals.lastUsedMs} should be in [$before, $after]")
    }

    @Test
    fun `multiple visits accumulate useCount`() {
        repeat(5) { store.recordVisit("/foo/bar") }
        val signals = store.getSignals("/foo/bar")
        assertEquals(5, signals.useCount)
    }

    @Test
    fun `recordVisit for two different paths tracks them independently`() {
        store.recordVisit("/foo/bar")
        store.recordVisit("/foo/bar")
        store.recordVisit("/baz/qux")
        assertEquals(2, store.getSignals("/foo/bar").useCount)
        assertEquals(1, store.getSignals("/baz/qux").useCount)
    }

    @Test
    fun `recordVisit with branch stores branch in signals`() {
        store.recordVisit("/foo/bar", branch = "main")
        val signals = store.getSignals("/foo/bar")
        assertEquals("main", signals.currentBranch)
    }

    @Test
    fun `recordVisit without branch leaves currentBranch null for new entry`() {
        store.recordVisit("/foo/bar")
        val signals = store.getSignals("/foo/bar")
        assertNull(signals.currentBranch)
    }

    @Test
    fun `branch stored as blank string is returned as null in UsageSignals`() {
        store.recordVisit("/foo/bar", branch = "")
        val signals = store.getSignals("/foo/bar")
        assertNull(signals.currentBranch,
            "A blank branch string should surface as null in UsageSignals")
    }

    // -------------------------------------------------------------------------
    // setBookmarked
    // -------------------------------------------------------------------------

    @Test
    fun `setBookmarked true is reflected in getSignals`() {
        store.setBookmarked("/my/path", true)
        val signals = store.getSignals("/my/path")
        assertTrue(signals.isBookmarked)
    }

    @Test
    fun `setBookmarked false clears bookmark flag`() {
        store.setBookmarked("/my/path", true)
        store.setBookmarked("/my/path", false)
        val signals = store.getSignals("/my/path")
        assertFalse(signals.isBookmarked)
    }

    @Test
    fun `setBookmarked creates entry even without a prior visit`() {
        store.setBookmarked("/brand/new/path", true)
        val signals = store.getSignals("/brand/new/path")
        assertTrue(signals.isBookmarked)
    }

    // -------------------------------------------------------------------------
    // recencyScore
    // -------------------------------------------------------------------------

    @Test
    fun `recencyScore for unknown path is zero`() {
        assertEquals(0.0, store.recencyScore("/not/visited"), 1e-9)
    }

    @Test
    fun `recencyScore for just-visited path is close to one`() {
        store.recordVisit("/fresh/path")
        val score = store.recencyScore("/fresh/path")
        // exp(0) = 1.0; a few ms of elapsed time should keep score > 0.999
        assertTrue(score > 0.999,
            "Freshly visited path should have recency score close to 1, got $score")
    }

    @Test
    fun `recencyScore is in range zero to one`() {
        store.recordVisit("/some/path")
        val score = store.recencyScore("/some/path")
        assertTrue(score in 0.0..1.0)
    }

    // -------------------------------------------------------------------------
    // frequencyScore
    // -------------------------------------------------------------------------

    @Test
    fun `frequencyScore for unknown path is zero`() {
        assertEquals(0.0, store.frequencyScore("/not/visited"), 1e-9)
    }

    @Test
    fun `frequencyScore is positive after a visit`() {
        store.recordVisit("/some/path")
        val score = store.frequencyScore("/some/path")
        assertTrue(score > 0.0)
    }

    @Test
    fun `frequencyScore is normalized to at most one`() {
        repeat(100) { store.recordVisit("/heavy/hitter") }
        val score = store.frequencyScore("/heavy/hitter")
        assertTrue(score <= 1.0,
            "Frequency score should not exceed 1.0, got $score")
    }

    @Test
    fun `frequencyScore increases monotonically with additional visits`() {
        store.recordVisit("/path")
        val after1 = store.frequencyScore("/path")
        store.recordVisit("/path")
        val after2 = store.frequencyScore("/path")
        assertTrue(after2 >= after1,
            "Frequency score should not decrease with more visits: after1=$after1 after2=$after2")
    }

    @Test
    fun `path with more visits has higher frequencyScore than less-visited path`() {
        repeat(10) { store.recordVisit("/frequent") }
        store.recordVisit("/infrequent")
        val freqScore = store.frequencyScore("/frequent")
        val infreqScore = store.frequencyScore("/infrequent")
        assertTrue(freqScore > infreqScore,
            "Frequently visited path should score higher: freq=$freqScore infreq=$infreqScore")
    }

    // -------------------------------------------------------------------------
    // State persistence round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `getState and loadState round-trip preserves visit count`() {
        store.recordVisit("/persist/me")
        store.recordVisit("/persist/me")
        val savedState = store.getState()

        val restoredStore = FrecencyStore()
        restoredStore.loadState(savedState)

        assertEquals(2, restoredStore.getSignals("/persist/me").useCount)
    }

    @Test
    fun `getState and loadState round-trip preserves isBookmarked`() {
        store.setBookmarked("/bookmarked/path", true)
        val savedState = store.getState()

        val restoredStore = FrecencyStore()
        restoredStore.loadState(savedState)

        assertTrue(restoredStore.getSignals("/bookmarked/path").isBookmarked)
    }

    @Test
    fun `getState and loadState round-trip preserves branch`() {
        store.recordVisit("/branch/path", branch = "feature-xyz")
        val savedState = store.getState()

        val restoredStore = FrecencyStore()
        restoredStore.loadState(savedState)

        assertEquals("feature-xyz", restoredStore.getSignals("/branch/path").currentBranch)
    }

    @Test
    fun `loadState replaces all existing entries`() {
        store.recordVisit("/old/path")
        val freshStore = FrecencyStore()
        freshStore.recordVisit("/new/path")
        val newState = freshStore.getState()

        store.loadState(newState)

        // Old path should no longer be present
        assertEquals(0, store.getSignals("/old/path").useCount)
        // New path should be accessible
        assertEquals(1, store.getSignals("/new/path").useCount)
    }

    @Test
    fun `maxUseCount in state is at least as large as any individual useCount`() {
        repeat(7) { store.recordVisit("/big/user") }
        store.recordVisit("/small/user")
        val state = store.getState()
        val max = state.maxUseCount
        assertTrue(max >= 7,
            "maxUseCount should be >= 7 but got $max")
    }
}
