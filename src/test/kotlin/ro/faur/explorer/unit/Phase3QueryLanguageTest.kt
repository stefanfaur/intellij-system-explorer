package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.query.ParsedQuery
import ro.faur.explorer.quickopen.query.QueryMode
import ro.faur.explorer.quickopen.query.QueryParser

/**
 * Phase 3 — Query Language Parser
 *
 * Tests every prefix mode, combined modifiers, and edge cases described in the
 * Success Criteria for Phase 3 Automated Verification.
 *
 * All tests will fail to compile until QueryParser, ParsedQuery, and QueryMode exist.
 */
class Phase3QueryLanguageTest {

    // -------------------------------------------------------------------------
    // Mode prefix tests
    // -------------------------------------------------------------------------

    @Test
    fun `blank query returns UNIFIED mode with empty searchText`() {
        val result = QueryParser.parse("")
        assertEquals(QueryMode.UNIFIED, result.mode)
        assertEquals("", result.searchText)
        assertNull(result.extensionFilter)
        assertNull(result.scopeFilter)
    }

    @Test
    fun `whitespace-only query returns UNIFIED mode with empty searchText`() {
        val result = QueryParser.parse("   ")
        assertEquals(QueryMode.UNIFIED, result.mode)
        assertEquals("", result.searchText)
    }

    @Test
    fun `d prefix sets DIRS_ONLY mode and strips prefix from searchText`() {
        val result = QueryParser.parse("d: src test")
        assertEquals(QueryMode.DIRS_ONLY, result.mode)
        assertEquals("src test", result.searchText)
    }

    @Test
    fun `d prefix with no trailing text produces empty searchText`() {
        val result = QueryParser.parse("d:")
        assertEquals(QueryMode.DIRS_ONLY, result.mode)
        assertEquals("", result.searchText)
    }

    @Test
    fun `f prefix sets FILES_ONLY mode and strips prefix`() {
        val result = QueryParser.parse("f: readme")
        assertEquals(QueryMode.FILES_ONLY, result.mode)
        assertEquals("readme", result.searchText)
    }

    @Test
    fun `b prefix sets BOOKMARKS_ONLY mode`() {
        val result = QueryParser.parse("b: work")
        assertEquals(QueryMode.BOOKMARKS_ONLY, result.mode)
        assertEquals("work", result.searchText)
    }

    @Test
    fun `r prefix sets RECENT_ONLY mode`() {
        val result = QueryParser.parse("r: project")
        assertEquals(QueryMode.RECENT_ONLY, result.mode)
        assertEquals("project", result.searchText)
    }

    @Test
    fun `greater-than prefix sets COMMAND mode`() {
        val result = QueryParser.parse(">toggle hidden")
        assertEquals(QueryMode.COMMAND, result.mode)
        assertEquals("toggle hidden", result.searchText)
    }

    @Test
    fun `greater-than prefix alone sets COMMAND mode with empty searchText`() {
        val result = QueryParser.parse(">")
        assertEquals(QueryMode.COMMAND, result.mode)
        assertEquals("", result.searchText)
    }

    @Test
    fun `tilde prefix sets REGEX mode`() {
        val result = QueryParser.parse("~src/.*/ui")
        assertEquals(QueryMode.REGEX, result.mode)
        assertEquals("src/.*/ui", result.searchText)
    }

    @Test
    fun `slash-colon prefix sets CONTENT_SEARCH mode`() {
        val result = QueryParser.parse("/:fun navigateTo")
        assertEquals(QueryMode.CONTENT_SEARCH, result.mode)
        assertEquals("fun navigateTo", result.searchText)
    }

    // -------------------------------------------------------------------------
    // @ext: modifier tests
    // -------------------------------------------------------------------------

    @Test
    fun `at-ext modifier sets extensionFilter in UNIFIED mode`() {
        val result = QueryParser.parse("@ext:kt")
        assertEquals(QueryMode.UNIFIED, result.mode)
        assertEquals("kt", result.extensionFilter)
        assertEquals("", result.searchText.trim())
    }

    @Test
    fun `at-ext modifier combined with FILES_ONLY mode`() {
        val result = QueryParser.parse("f: @ext:kt")
        assertEquals(QueryMode.FILES_ONLY, result.mode)
        assertEquals("kt", result.extensionFilter)
    }

    @Test
    fun `at-ext and at-in combined with FILES_ONLY mode`() {
        val result = QueryParser.parse("f: @ext:kt @in:src")
        assertEquals(QueryMode.FILES_ONLY, result.mode)
        assertEquals("kt", result.extensionFilter)
        assertEquals("src", result.scopeFilter)
    }

    @Test
    fun `at-in modifier sets scopeFilter`() {
        val result = QueryParser.parse("foo @in:src/main")
        assertNotNull(result.scopeFilter)
        assertEquals("src/main", result.scopeFilter)
        assertEquals("foo", result.searchText.trim())
    }

    @Test
    fun `at-ext modifier is stripped from searchText`() {
        val result = QueryParser.parse("myQuery @ext:xml")
        assertFalse(result.searchText.contains("@ext:"))
        assertEquals("xml", result.extensionFilter)
    }

    @Test
    fun `at-in modifier is stripped from searchText`() {
        val result = QueryParser.parse("myQuery @in:src")
        assertFalse(result.searchText.contains("@in:"))
        assertEquals("src", result.scopeFilter)
    }

    // -------------------------------------------------------------------------
    // rawText preservation
    // -------------------------------------------------------------------------

    @Test
    fun `rawText is preserved exactly as input`() {
        val raw = "d: src @ext:kt @in:main"
        val result = QueryParser.parse(raw)
        assertEquals(raw, result.rawText)
    }

    @Test
    fun `plain query without prefix stays UNIFIED`() {
        val result = QueryParser.parse("readme")
        assertEquals(QueryMode.UNIFIED, result.mode)
        assertEquals("readme", result.searchText)
        assertNull(result.extensionFilter)
        assertNull(result.scopeFilter)
    }

    @Test
    fun `query starting with colon but no known prefix stays UNIFIED`() {
        // A colon in the middle should not trigger a mode
        val result = QueryParser.parse("foo:bar")
        assertEquals(QueryMode.UNIFIED, result.mode)
    }
}
