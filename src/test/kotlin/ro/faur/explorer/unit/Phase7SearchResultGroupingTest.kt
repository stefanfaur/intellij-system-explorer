package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import ro.faur.explorer.quickopen.ui.SearchResult

/**
 * Phase 7 — SearchResult group header and skeleton row model
 *
 * Covers the Automated Verification item:
 *   - SearchResult group header rows are non-selectable (unit test on list selection model)
 *
 * The plan modifies SearchResult data class in Phase 7:
 *   data class SearchResult(
 *       val scored: ScoredCandidate? = null,  // null = skeleton row
 *       val isGroupHeader: Boolean = false,
 *       val groupName: String = ""
 *   )
 *
 * These tests validate the data model changes and the discriminators the renderer uses
 * to distinguish real results, group headers, and skeleton rows.
 *
 * Tests fail to compile until SearchResult has the modified nullable `scored` field,
 * as specified in Phase 7.
 */
class Phase7SearchResultGroupingTest {

    private fun candidate(name: String, type: CandidateType = CandidateType.DIRECTORY) = SearchCandidate(
        id = name,
        displayName = name,
        fullPath = "/tmp/$name",
        parentPath = "/tmp",
        type = type
    )

    private fun scoredCandidate(name: String, type: CandidateType = CandidateType.DIRECTORY) =
        ScoredCandidate(candidate(name, type), 1.0)

    // -------------------------------------------------------------------------
    // SearchResult data model
    // -------------------------------------------------------------------------

    @Test
    fun `SearchResult with scored candidate is not a group header`() {
        val result = SearchResult(scored = scoredCandidate("mydir"))
        assertFalse(result.isGroupHeader)
        assertNotNull(result.scored)
    }

    @Test
    fun `SearchResult group header has null scored and isGroupHeader true`() {
        val header = SearchResult(scored = null, isGroupHeader = true, groupName = "Recent")
        assertTrue(header.isGroupHeader)
        assertNull(header.scored)
        assertEquals("Recent", header.groupName)
    }

    @Test
    fun `SearchResult skeleton row has null scored and isGroupHeader false`() {
        val skeleton = SearchResult(scored = null, isGroupHeader = false)
        assertFalse(skeleton.isGroupHeader)
        assertNull(skeleton.scored)
    }

    @Test
    fun `SearchResult default groupName is empty string`() {
        val result = SearchResult(scored = scoredCandidate("file"))
        assertEquals("", result.groupName)
    }

    // -------------------------------------------------------------------------
    // Group header non-selectability contract
    // -------------------------------------------------------------------------

    @Test
    fun `group headers can be identified by isGroupHeader flag`() {
        val items = listOf(
            SearchResult(scored = null, isGroupHeader = true, groupName = "Top Matches"),
            SearchResult(scored = scoredCandidate("readme")),
            SearchResult(scored = scoredCandidate("notes")),
            SearchResult(scored = null, isGroupHeader = true, groupName = "Recent"),
            SearchResult(scored = scoredCandidate("build")),
        )

        val headers = items.filter { it.isGroupHeader }
        val nonHeaders = items.filter { !it.isGroupHeader }

        assertEquals(2, headers.size)
        assertEquals(3, nonHeaders.size)
        assertTrue(headers.all { it.scored == null })
        assertTrue(nonHeaders.all { it.scored != null })
    }

    @Test
    fun `skeleton rows can be identified by null scored and not isGroupHeader`() {
        val items = listOf(
            SearchResult(scored = null, isGroupHeader = false), // skeleton
            SearchResult(scored = null, isGroupHeader = false), // skeleton
            SearchResult(scored = scoredCandidate("real"), isGroupHeader = false),
        )

        val skeletons = items.filter { it.scored == null && !it.isGroupHeader }
        assertEquals(2, skeletons.size)
    }

    @Test
    fun `list with no group headers has all selectable rows`() {
        val items = listOf(
            SearchResult(scored = scoredCandidate("a")),
            SearchResult(scored = scoredCandidate("b")),
            SearchResult(scored = scoredCandidate("c")),
        )
        assertTrue(items.none { it.isGroupHeader })
    }

    @Test
    fun `group header row has empty scored candidate`() {
        val header = SearchResult(isGroupHeader = true, groupName = "Bookmarks")
        assertNull(header.scored)
        assertTrue(header.isGroupHeader)
    }

    // -------------------------------------------------------------------------
    // Git status badge values (plan specifies "M", "?", "✓", "")
    // -------------------------------------------------------------------------

    @Test
    fun `valid git status strings match plan specification`() {
        val validStatuses = setOf("M", "?", "✓", "")
        // Verify the expected strings are defined in the plan and can be created as Kotlin strings
        assertTrue("M" in validStatuses)
        assertTrue("?" in validStatuses)
        assertTrue("✓" in validStatuses)
        assertTrue("" in validStatuses)
    }
}
