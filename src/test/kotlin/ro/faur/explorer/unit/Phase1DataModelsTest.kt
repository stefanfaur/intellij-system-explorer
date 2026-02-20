package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import ro.faur.explorer.quickopen.model.UsageSignals

/**
 * Phase 1 — Data Model Correctness
 *
 * Tests that the core data models have the right field defaults, copy semantics,
 * and structural invariants described in the plan. These are pure-Kotlin data
 * class tests; no IntelliJ platform dependency needed.
 */
class Phase1DataModelsTest {

    // -------------------------------------------------------------------------
    // UsageSignals
    // -------------------------------------------------------------------------

    @Test
    fun `UsageSignals has zero lastUsedMs by default`() {
        val signals = UsageSignals()
        assertEquals(0L, signals.lastUsedMs)
    }

    @Test
    fun `UsageSignals has zero useCount by default`() {
        val signals = UsageSignals()
        assertEquals(0, signals.useCount)
    }

    @Test
    fun `UsageSignals isBookmarked is false by default`() {
        val signals = UsageSignals()
        assertFalse(signals.isBookmarked)
    }

    @Test
    fun `UsageSignals isOpenInEditor is false by default`() {
        val signals = UsageSignals()
        assertFalse(signals.isOpenInEditor)
    }

    @Test
    fun `UsageSignals currentBranch is null by default`() {
        val signals = UsageSignals()
        assertNull(signals.currentBranch)
    }

    @Test
    fun `UsageSignals copy preserves unmodified fields`() {
        val original = UsageSignals(lastUsedMs = 1000L, useCount = 5, isBookmarked = true)
        val modified = original.copy(isBookmarked = false)
        assertEquals(1000L, modified.lastUsedMs)
        assertEquals(5, modified.useCount)
        assertFalse(modified.isBookmarked)
    }

    @Test
    fun `UsageSignals data class equality compares by value`() {
        val a = UsageSignals(lastUsedMs = 42L, useCount = 3, isBookmarked = false)
        val b = UsageSignals(lastUsedMs = 42L, useCount = 3, isBookmarked = false)
        assertEquals(a, b)
    }

    @Test
    fun `UsageSignals with different useCount are not equal`() {
        val a = UsageSignals(useCount = 1)
        val b = UsageSignals(useCount = 2)
        assertNotEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // SearchCandidate
    // -------------------------------------------------------------------------

    @Test
    fun `SearchCandidate stores id exactly as provided`() {
        val candidate = makeCandidate(id = "bm:/foo/bar")
        assertEquals("bm:/foo/bar", candidate.id)
    }

    @Test
    fun `SearchCandidate stores displayName exactly as provided`() {
        val candidate = makeCandidate(displayName = "bar")
        assertEquals("bar", candidate.displayName)
    }

    @Test
    fun `SearchCandidate stores fullPath exactly as provided`() {
        val candidate = makeCandidate(fullPath = "/foo/bar")
        assertEquals("/foo/bar", candidate.fullPath)
    }

    @Test
    fun `SearchCandidate stores parentPath exactly as provided`() {
        val candidate = makeCandidate(parentPath = "/foo")
        assertEquals("/foo", candidate.parentPath)
    }

    @Test
    fun `SearchCandidate signals defaults to empty UsageSignals`() {
        val candidate = makeCandidate()
        assertEquals(UsageSignals(), candidate.signals)
    }

    @Test
    fun `SearchCandidate contentSnippet is null by default`() {
        val candidate = makeCandidate()
        assertNull(candidate.contentSnippet)
    }

    @Test
    fun `SearchCandidate extra map is empty by default`() {
        val candidate = makeCandidate()
        assertTrue(candidate.extra.isEmpty())
    }

    @Test
    fun `SearchCandidate extra map stores arbitrary values`() {
        val candidate = makeCandidate(extra = mapOf("childCount" to 7, "gitStatus" to "M"))
        assertEquals(7, candidate.extra["childCount"])
        assertEquals("M", candidate.extra["gitStatus"])
    }

    @Test
    fun `SearchCandidate copy with different type does not mutate original`() {
        val original = makeCandidate(type = CandidateType.DIRECTORY)
        val copy = original.copy(type = CandidateType.BOOKMARK)
        assertEquals(CandidateType.DIRECTORY, original.type)
        assertEquals(CandidateType.BOOKMARK, copy.type)
    }

    @Test
    fun `SearchCandidate data class equality is structural`() {
        val a = makeCandidate(id = "x", displayName = "x", fullPath = "/x")
        val b = makeCandidate(id = "x", displayName = "x", fullPath = "/x")
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // CandidateType
    // -------------------------------------------------------------------------

    @Test
    fun `CandidateType enum contains DIRECTORY`() {
        assertNotNull(CandidateType.DIRECTORY)
    }

    @Test
    fun `CandidateType enum contains FILE`() {
        assertNotNull(CandidateType.FILE)
    }

    @Test
    fun `CandidateType enum contains BOOKMARK`() {
        assertNotNull(CandidateType.BOOKMARK)
    }

    @Test
    fun `CandidateType enum contains RECENT`() {
        assertNotNull(CandidateType.RECENT)
    }

    @Test
    fun `CandidateType enum contains OPEN_EDITOR`() {
        assertNotNull(CandidateType.OPEN_EDITOR)
    }

    @Test
    fun `CandidateType enum contains ACTION`() {
        assertNotNull(CandidateType.ACTION)
    }

    @Test
    fun `CandidateType enum contains CONTENT_MATCH`() {
        assertNotNull(CandidateType.CONTENT_MATCH)
    }

    @Test
    fun `CandidateType has exactly seven values`() {
        assertEquals(7, CandidateType.values().size)
    }

    // -------------------------------------------------------------------------
    // ScoredCandidate
    // -------------------------------------------------------------------------

    @Test
    fun `ScoredCandidate wraps candidate reference without copying`() {
        val candidate = makeCandidate()
        val scored = ScoredCandidate(candidate = candidate, score = 0.9)
        assertSame(candidate, scored.candidate)
    }

    @Test
    fun `ScoredCandidate matchedRanges defaults to empty list`() {
        val scored = ScoredCandidate(candidate = makeCandidate(), score = 1.0)
        assertTrue(scored.matchedRanges.isEmpty())
    }

    @Test
    fun `ScoredCandidate stores score exactly`() {
        val scored = ScoredCandidate(candidate = makeCandidate(), score = 0.753)
        assertEquals(0.753, scored.score, 1e-9)
    }

    @Test
    fun `ScoredCandidate copy with updated score preserves matchedRanges`() {
        val ranges = listOf(0..2, 5..7)
        val original = ScoredCandidate(candidate = makeCandidate(), score = 0.5, matchedRanges = ranges)
        val updated = original.copy(score = 0.9)
        assertEquals(ranges, updated.matchedRanges)
    }

    @Test
    fun `ScoredCandidate stores non-empty matchedRanges`() {
        val ranges = listOf(1..3, 6..8)
        val scored = ScoredCandidate(candidate = makeCandidate(), score = 0.8, matchedRanges = ranges)
        assertEquals(ranges, scored.matchedRanges)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeCandidate(
        id: String = "test:id",
        displayName: String = "testFile",
        fullPath: String = "/some/path/testFile",
        parentPath: String = "/some/path",
        type: CandidateType = CandidateType.FILE,
        signals: UsageSignals = UsageSignals(),
        extra: Map<String, Any> = emptyMap()
    ) = SearchCandidate(
        id = id,
        displayName = displayName,
        fullPath = fullPath,
        parentPath = parentPath,
        type = type,
        signals = signals,
        extra = extra
    )
}
