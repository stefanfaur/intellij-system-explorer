package ro.faur.explorer.unit

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import ro.faur.explorer.quickopen.backend.ContentMatch
import ro.faur.explorer.quickopen.backend.RipgrepContentSearch
import java.io.File

/**
 * Phase 6 — RipgrepContentSearch unit tests
 *
 * Covers the Automated Verification item:
 *   - RipgrepContentSearch.search() returns at least one result for a known pattern in the project
 *
 * Tests that require rg to be installed are guarded with assumeTrue.
 *
 * Tests fail to compile until RipgrepContentSearch exists under
 * ro.faur.explorer.quickopen.backend.
 */
class Phase6RipgrepContentSearchTest {

    private val projectRoot = System.getProperty("user.dir") ?: "/tmp"

    private fun rgAvailable(): String? {
        return try {
            val p = ProcessBuilder("rg", "--version").start()
            if (p.waitFor() == 0) "rg" else null
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // Constructor / instantiation
    // -------------------------------------------------------------------------

    @Test
    fun `RipgrepContentSearch can be instantiated with a scope`() {
        val searcher = RipgrepContentSearch(scope = "/tmp")
        assertNotNull(searcher)
    }

    @Test
    fun `search with blank pattern emits no results`() = runBlocking {
        val searcher = RipgrepContentSearch(scope = projectRoot)
        val results = searcher.search("").toList()
        assertTrue(results.isEmpty(), "Blank pattern should emit no ContentMatch objects")
    }

    // -------------------------------------------------------------------------
    // Real rg search (skipped when rg absent)
    // -------------------------------------------------------------------------

    @Test
    fun `search returns results for a known pattern in a temp directory`() = runBlocking {
        val rg = rgAvailable()
        assumeTrue(rg != null, "rg not installed — skipping content search test")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_content_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val testFile = File(tempDir, "sample.kt")
            testFile.writeText("fun navigateTo(path: String) {\n    println(path)\n}\n")

            val searcher = RipgrepContentSearch(rgPath = rg!!, scope = tempDir.absolutePath)
            val results = searcher.search("navigateTo").toList()

            assertTrue(results.isNotEmpty(), "Should find at least one match for 'navigateTo'")
            assertTrue(
                results.any { it.filePath.endsWith("sample.kt") },
                "Should find a match in sample.kt"
            )
            assertTrue(
                results.any { it.snippet.contains("navigateTo") },
                "Snippet should contain the search pattern"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `search respects MAX_CONTENT_RESULTS cap`() = runBlocking {
        val rg = rgAvailable()
        assumeTrue(rg != null, "rg not installed — skipping cap test")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_cap_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // Create a file with many matching lines
            val lines = (1..300).joinToString("\n") { "match line $it" }
            File(tempDir, "bigfile.txt").writeText(lines)

            val searcher = RipgrepContentSearch(rgPath = rg!!, scope = tempDir.absolutePath)
            val results = searcher.search("match line").toList()

            assertTrue(
                results.size <= RipgrepContentSearch.DEFAULT_MAX_CONTENT_RESULTS,
                "Results should be capped at ${RipgrepContentSearch.DEFAULT_MAX_CONTENT_RESULTS}, got ${results.size}"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `search on non-existent scope does not throw`() = runBlocking {
        val searcher = RipgrepContentSearch(scope = "/this/path/does/not/exist/xyz_abc")
        val result = runCatching { searcher.search("anyPattern").toList() }
        assertTrue(result.isSuccess, "search() on non-existent scope must not throw: ${result.exceptionOrNull()}")
    }

    @Test
    fun `search line numbers are positive integers`() = runBlocking {
        val rg = rgAvailable()
        assumeTrue(rg != null, "rg not installed — skipping line number test")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_line_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            File(tempDir, "code.kt").writeText("line one\ntarget pattern here\nline three\n")

            val searcher = RipgrepContentSearch(rgPath = rg!!, scope = tempDir.absolutePath)
            val results = searcher.search("target pattern").toList()

            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.lineNumber > 0 }, "All line numbers should be positive")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // JSON parsing and matchRanges
    // -------------------------------------------------------------------------

    @Test
    fun `ContentMatch parse returns matchRanges from JSON submatch`() {
        val json = """{"type":"match","data":{"path":{"text":"/tmp/foo.kt"},"lines":{"text":"fun navigateTo(path: String)\n"},"line_number":42,"absolute_offset":0,"submatches":[{"match":{"text":"navigateTo"},"start":4,"end":14}]}}"""
        val match = ContentMatch.parseJson(json)
        assertNotNull(match)
        assertEquals("/tmp/foo.kt", match!!.filePath)
        assertEquals(42, match.lineNumber)
        assertTrue(match.snippet.contains("navigateTo"))
        assertEquals(1, match.matchRanges.size)
        assertEquals(4, match.matchRanges[0].first)
        assertEquals(13, match.matchRanges[0].last)  // end=14 exclusive → last=13
    }

    @Test
    fun `ContentMatch parseJson returns null for non-match type`() {
        val json = """{"type":"begin","data":{"path":{"text":"/tmp/foo.kt"}}}"""
        assertNull(ContentMatch.parseJson(json))
    }

    @Test
    fun `ContentMatch parseJson handles multibyte chars in byte-to-char conversion`() {
        // "café" is 5 bytes (c=1, a=1, f=1, é=2) but 4 chars
        // submatch at bytes 3..5 (é) should map to chars 3..4
        val json = """{"type":"match","data":{"path":{"text":"/tmp/f.kt"},"lines":{"text":"café\n"},"line_number":1,"absolute_offset":0,"submatches":[{"match":{"text":"é"},"start":3,"end":5}]}}"""
        val match = ContentMatch.parseJson(json)
        assertNotNull(match)
        assertEquals(1, match!!.matchRanges.size)
        assertEquals(3, match.matchRanges[0].first)
        assertEquals(3, match.matchRanges[0].last)  // é is one char at index 3
    }

    @Test
    fun `search results contain non-empty matchRanges for real rg`() = runBlocking {
        val rg = rgAvailable()
        assumeTrue(rg != null, "rg not installed")
        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_ranges_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            File(tempDir, "sample.kt").writeText("fun navigateTo(path: String) {}\n")
            val results = RipgrepContentSearch(rgPath = rg!!, scope = tempDir.absolutePath)
                .search("navigateTo").toList()
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.matchRanges.isNotEmpty() }, "matchRanges should be populated from JSON")
        } finally { tempDir.deleteRecursively() }
    }
}
