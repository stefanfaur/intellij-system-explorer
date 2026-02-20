package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.ranking.FrecencyStore

/**
 * Phase 7 — Branch-context frecency
 *
 * The plan adds branch-aware scoring: when recordVisit() is called with a branch,
 * and the current branch matches the stored branch, a branchBoost of +0.03 is applied.
 *
 * Phase 7 expands the existing FrecencyStore.recordVisit(path, branch) to actually
 * store and surface the branch, then Ranker.rank() uses it.
 *
 * These tests verify the FrecencyStore side of this contract:
 *   - recordVisit with a branch stores the branch in signals.currentBranch
 *   - recordVisit without a branch leaves currentBranch null
 *   - overwriting a visit with a new branch updates the stored branch
 *
 * Note: Phase 1 FrecencyStoreUnitTest already tests state round-trip with branch.
 * These tests specifically target the Phase 7 requirement that the branch is used
 * for scoring (we test the data storage; the scoring is verified in RankerTest).
 *
 * Tests fail if FrecencyStore does not store/surface branch in UsageSignals.
 */
class Phase7BranchFrecencyTest {

    private lateinit var store: FrecencyStore

    @BeforeEach
    fun setup() {
        store = FrecencyStore()
        store.loadState(FrecencyStore.State())
    }

    @Test
    fun `recordVisit with branch stores branch in signals`() {
        store.recordVisit("/project/file.kt", "feature/my-feature")
        val signals = store.getSignals("/project/file.kt")
        assertEquals("feature/my-feature", signals.currentBranch)
    }

    @Test
    fun `recordVisit without branch leaves currentBranch null`() {
        store.recordVisit("/project/file.kt")
        val signals = store.getSignals("/project/file.kt")
        assertNull(signals.currentBranch)
    }

    @Test
    fun `recordVisit with null branch leaves currentBranch null`() {
        store.recordVisit("/project/file.kt", null)
        val signals = store.getSignals("/project/file.kt")
        assertNull(signals.currentBranch)
    }

    @Test
    fun `second recordVisit with different branch updates stored branch`() {
        store.recordVisit("/project/file.kt", "main")
        store.recordVisit("/project/file.kt", "feature/new")
        val signals = store.getSignals("/project/file.kt")
        assertEquals("feature/new", signals.currentBranch)
    }

    @Test
    fun `paths visited on different branches have distinct branch signals`() {
        store.recordVisit("/path/a", "main")
        store.recordVisit("/path/b", "feature/x")

        assertEquals("main", store.getSignals("/path/a").currentBranch)
        assertEquals("feature/x", store.getSignals("/path/b").currentBranch)
    }

    @Test
    fun `branch signal survives state round-trip`() {
        store.recordVisit("/roundtrip", "develop")
        val state = store.getState()

        val newStore = FrecencyStore()
        newStore.loadState(state)

        assertEquals("develop", newStore.getSignals("/roundtrip").currentBranch)
    }

    @Test
    fun `blank branch string is treated as null branch`() {
        // The plan uses entry.branch.ifBlank { null } in getSignals()
        store.recordVisit("/path", "   ")
        val signals = store.getSignals("/path")
        // Blank branch should surface as null
        assertNull(signals.currentBranch)
    }
}
