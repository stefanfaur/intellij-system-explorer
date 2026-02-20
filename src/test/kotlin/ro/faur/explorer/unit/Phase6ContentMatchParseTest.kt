package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.ContentMatch

/**
 * Phase 6 — ContentMatch.parse() unit tests
 *
 * Covers the Automated Verification item:
 *   - ContentMatch.parse() unit tests: known rg output lines → correct ContentMatch objects
 *
 * ripgrep --no-heading format: path:line:content
 *
 * Tests fail to compile until ContentMatch exists under
 * ro.faur.explorer.quickopen.backend.
 */
class Phase6ContentMatchParseTest {

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    fun `parse standard rg output line produces correct ContentMatch`() {
        val line = "/home/user/project/src/Main.kt:42:fun navigateTo(path: String)"
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertEquals("/home/user/project/src/Main.kt", result!!.filePath)
        assertEquals(42, result.lineNumber)
        assertEquals("fun navigateTo(path: String)", result.snippet)
    }

    @Test
    fun `parse line number 1 is valid`() {
        val line = "/path/to/file.txt:1:first line content"
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertEquals(1, result!!.lineNumber)
    }

    @Test
    fun `parse line where content contains colons`() {
        // content after the line number may itself contain colons — only split on first 2 colons
        val line = "/path/file.kt:10:val url = \"http://example.com:8080/path\""
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertEquals("/path/file.kt", result!!.filePath)
        assertEquals(10, result.lineNumber)
        // The snippet should include the colon-containing content
        assertTrue(result.snippet.contains("http://"), "Snippet should preserve content colons")
    }

    @Test
    fun `parse deep nested path works`() {
        val line = "/a/b/c/d/e/f.py:999:import os"
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertEquals("/a/b/c/d/e/f.py", result!!.filePath)
        assertEquals(999, result.lineNumber)
        assertEquals("import os", result.snippet)
    }

    // -------------------------------------------------------------------------
    // Snippet truncation (plan says .take(120))
    // -------------------------------------------------------------------------

    @Test
    fun `snippet is truncated to 120 characters when content is very long`() {
        val longContent = "x".repeat(200)
        val line = "/path/file.kt:5:$longContent"
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertTrue(
            result!!.snippet.length <= 120,
            "Snippet should be at most 120 chars, got ${result.snippet.length}"
        )
    }

    @Test
    fun `snippet is not padded when content is shorter than 120 characters`() {
        val shortContent = "short"
        val line = "/path/file.kt:5:$shortContent"
        val result = ContentMatch.parse(line)
        assertNotNull(result)
        assertEquals("short", result!!.snippet)
    }

    // -------------------------------------------------------------------------
    // Invalid / malformed input
    // -------------------------------------------------------------------------

    @Test
    fun `parse returns null for blank line`() {
        val result = ContentMatch.parse("")
        assertNull(result)
    }

    @Test
    fun `parse returns null when line number is not an integer`() {
        val line = "/path/file.kt:not-a-number:content"
        val result = ContentMatch.parse(line)
        assertNull(result)
    }

    @Test
    fun `parse returns null for line with only one colon`() {
        val line = "/path/file.kt:content-no-line-number"
        val result = ContentMatch.parse(line)
        assertNull(result)
    }

    @Test
    fun `parse returns null for line with two colons but empty line number`() {
        val line = "/path/file.kt::content"
        val result = ContentMatch.parse(line)
        assertNull(result)
    }

    @Test
    fun `parse handles Windows-style paths gracefully`() {
        // On Windows rg output may use drive letters like C:\path
        // The parser should not crash even on unusual input
        val result = runCatching { ContentMatch.parse("C:\\path\\file.kt:10:content") }
        assertTrue(result.isSuccess, "parse() must not throw on Windows-style paths")
    }
}
