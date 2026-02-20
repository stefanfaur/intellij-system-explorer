package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.ranking.FrecencyStore

/**
 * Tests FrecencyStore internal logic without needing a running application.
 * Instantiates the store directly rather than using the service registry.
 */
class FrecencyStoreUnitTest {

    private lateinit var store: FrecencyStore

    @BeforeEach
    fun setup() {
        store = FrecencyStore()
        // Load empty state
        store.loadState(FrecencyStore.State())
    }

    @Test
    fun `recordVisit increments useCount and updates lastUsedMs`() {
        val path = "/some/path"
        val before = System.currentTimeMillis()
        store.recordVisit(path)
        val signals = store.getSignals(path)
        assertEquals(1, signals.useCount)
        assertTrue(signals.lastUsedMs >= before)
    }

    @Test
    fun `multiple visits accumulate useCount`() {
        val path = "/other/path"
        repeat(5) { store.recordVisit(path) }
        assertEquals(5, store.getSignals(path).useCount)
    }

    @Test
    fun `unknown path returns zero signals`() {
        val signals = store.getSignals("/never/visited")
        assertEquals(0, signals.useCount)
        assertEquals(0L, signals.lastUsedMs)
        assertFalse(signals.isBookmarked)
    }

    @Test
    fun `setBookmarked marks path as bookmarked`() {
        val path = "/bookmarked/path"
        store.setBookmarked(path, true)
        assertTrue(store.getSignals(path).isBookmarked)
    }

    @Test
    fun `setBookmarked can unmark a bookmarked path`() {
        val path = "/bookmarked/path"
        store.setBookmarked(path, true)
        store.setBookmarked(path, false)
        assertFalse(store.getSignals(path).isBookmarked)
    }

    @Test
    fun `recencyScore is zero for unknown path`() {
        assertEquals(0.0, store.recencyScore("/unknown"), 0.001)
    }

    @Test
    fun `recencyScore is close to 1 for recently visited path`() {
        store.recordVisit("/recent")
        val score = store.recencyScore("/recent")
        assertTrue(score > 0.99, "Expected recency score close to 1, got $score")
    }

    @Test
    fun `frequencyScore is zero for unknown path`() {
        assertEquals(0.0, store.frequencyScore("/unknown"), 0.001)
    }

    @Test
    fun `frequencyScore increases with more visits`() {
        val path1 = "/path1"
        val path2 = "/path2"
        store.recordVisit(path1)
        store.recordVisit(path2)
        store.recordVisit(path2)
        val score1 = store.frequencyScore(path1)
        val score2 = store.frequencyScore(path2)
        assertTrue(score2 > score1, "path2 with more visits should have higher freq score")
    }

    @Test
    fun `state round-trip preserves entries`() {
        store.recordVisit("/trip/path", "main")
        store.setBookmarked("/trip/path", true)

        val savedState = store.getState()

        val newStore = FrecencyStore()
        newStore.loadState(savedState)

        val signals = newStore.getSignals("/trip/path")
        assertEquals(1, signals.useCount)
        assertTrue(signals.isBookmarked)
        assertEquals("main", signals.currentBranch)
    }
}
