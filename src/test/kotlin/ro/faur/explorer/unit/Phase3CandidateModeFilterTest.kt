package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.query.QueryMode
import ro.faur.explorer.quickopen.query.QueryParser

/**
 * Phase 3 — Mode filtering contract
 *
 * The plan defines how each query mode restricts candidate types:
 *   DIRS_ONLY   → DIRECTORY, BOOKMARK, RECENT pass; FILE, ACTION rejected
 *   FILES_ONLY  → FILE, OPEN_EDITOR pass; DIRECTORY, ACTION rejected
 *   BOOKMARKS_ONLY → only BOOKMARK
 *   RECENT_ONLY    → only RECENT
 *   COMMAND        → only ACTION
 *
 * These tests validate the parsing side only (no need for a running IntelliJ).
 * The `matchesMode` logic lives inside QuickOpenPanel but the QueryParser
 * produces the mode flag that drives it — we test the parsing contract here.
 *
 * Companion tests that require a live panel are in Phase3QueryFilterLightTest.
 */
class Phase3CandidateModeFilterTest {

    // -------------------------------------------------------------------------
    // Verify QueryParser produces the correct mode so callers can filter correctly
    // -------------------------------------------------------------------------

    @Test
    fun `d prefix produces DIRS_ONLY mode`() {
        assertEquals(QueryMode.DIRS_ONLY, QueryParser.parse("d: foo").mode)
    }

    @Test
    fun `f prefix produces FILES_ONLY mode`() {
        assertEquals(QueryMode.FILES_ONLY, QueryParser.parse("f: bar").mode)
    }

    @Test
    fun `b prefix produces BOOKMARKS_ONLY mode`() {
        assertEquals(QueryMode.BOOKMARKS_ONLY, QueryParser.parse("b: work").mode)
    }

    @Test
    fun `r prefix produces RECENT_ONLY mode`() {
        assertEquals(QueryMode.RECENT_ONLY, QueryParser.parse("r: docs").mode)
    }

    @Test
    fun `command prefix produces COMMAND mode`() {
        assertEquals(QueryMode.COMMAND, QueryParser.parse(">toggle").mode)
    }

    @Test
    fun `tilde produces REGEX mode`() {
        assertEquals(QueryMode.REGEX, QueryParser.parse("~.*kotlin").mode)
    }

    @Test
    fun `slash-colon produces CONTENT_SEARCH mode`() {
        assertEquals(QueryMode.CONTENT_SEARCH, QueryParser.parse("/:searchMe").mode)
    }

    // -------------------------------------------------------------------------
    // Verify all CandidateType enum values exist as expected by the plan
    // -------------------------------------------------------------------------

    @Test
    fun `DIRECTORY candidate type exists`() {
        val c = SearchCandidate("id", "name", "/path", "/", CandidateType.DIRECTORY)
        assertEquals(CandidateType.DIRECTORY, c.type)
    }

    @Test
    fun `FILE candidate type exists`() {
        val c = SearchCandidate("id", "name", "/path/file.kt", "/path", CandidateType.FILE)
        assertEquals(CandidateType.FILE, c.type)
    }

    @Test
    fun `BOOKMARK candidate type exists`() {
        val c = SearchCandidate("id", "name", "/path", "/", CandidateType.BOOKMARK)
        assertEquals(CandidateType.BOOKMARK, c.type)
    }

    @Test
    fun `RECENT candidate type exists`() {
        val c = SearchCandidate("id", "name", "/path", "/", CandidateType.RECENT)
        assertEquals(CandidateType.RECENT, c.type)
    }

    @Test
    fun `OPEN_EDITOR candidate type exists`() {
        val c = SearchCandidate("id", "name", "/path", "/", CandidateType.OPEN_EDITOR)
        assertEquals(CandidateType.OPEN_EDITOR, c.type)
    }

    @Test
    fun `ACTION candidate type exists`() {
        val c = SearchCandidate("id", "Toggle Hidden", "action:toggle", "", CandidateType.ACTION)
        assertEquals(CandidateType.ACTION, c.type)
    }

    @Test
    fun `CONTENT_MATCH candidate type exists`() {
        val c = SearchCandidate("id", "file.kt", "/path/file.kt", "/path", CandidateType.CONTENT_MATCH)
        assertEquals(CandidateType.CONTENT_MATCH, c.type)
    }
}
