package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

class SearchCandidateMatchRangesTest {

    @Test
    fun `SearchCandidate accepts null contentMatchRanges by default`() {
        val c = SearchCandidate(
            id = "x", displayName = "foo", fullPath = "/foo",
            parentPath = "/", type = CandidateType.FILE
        )
        assertNull(c.contentMatchRanges)
    }

    @Test
    fun `SearchCandidate stores provided contentMatchRanges`() {
        val ranges = listOf(2 until 7, 10 until 15)
        val c = SearchCandidate(
            id = "x", displayName = "foo", fullPath = "/foo",
            parentPath = "/", type = CandidateType.CONTENT_MATCH,
            contentMatchRanges = ranges
        )
        assertEquals(ranges, c.contentMatchRanges)
    }
}
