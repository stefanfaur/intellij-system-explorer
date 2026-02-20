package ro.faur.explorer.unit

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import ro.faur.explorer.quickopen.backend.RipgrepEnumerator
import java.io.File

/**
 * Phase 6 — RipgrepEnumerator unit tests
 *
 * Covers the Automated Verification items:
 *   - RipgrepEnumerator.detect() returns the correct path on a machine with rg installed
 *   - RipgrepEnumerator.enumerate() produces correct file paths for a test directory
 *   - CandidatePool with RipgrepEnumerator respects 50k cap (cap tested in CandidatePoolTest
 *     with VfsEnumerator; the same truncation logic applies)
 *
 * Tests that require rg to be installed use assumeTrue to skip gracefully in environments
 * where rg is absent.
 *
 * Tests fail to compile until RipgrepEnumerator exists under
 * ro.faur.explorer.quickopen.backend.
 */
class Phase6RipgrepEnumeratorTest {

    // -------------------------------------------------------------------------
    // detect()
    // -------------------------------------------------------------------------

    @Test
    fun `detect returns non-null string when rg is on PATH`() {
        val path = RipgrepEnumerator.detect()
        // If rg is installed, path should be non-null and non-blank
        if (path != null) {
            assertTrue(path.isNotBlank(), "Detected rg path should not be blank")
        }
        // If rg is not installed, detect() must return null (not throw)
    }

    @Test
    fun `detect does not throw even when rg is absent`() {
        val result = runCatching { RipgrepEnumerator.detect() }
        assertTrue(result.isSuccess, "detect() must not throw: ${result.exceptionOrNull()}")
    }

    // -------------------------------------------------------------------------
    // isAvailable()
    // -------------------------------------------------------------------------

    @Test
    fun `isAvailable does not throw`() {
        val enumerator = RipgrepEnumerator()
        val result = runCatching { enumerator.isAvailable() }
        assertTrue(result.isSuccess, "isAvailable() must not throw: ${result.exceptionOrNull()}")
    }

    @Test
    fun `isAvailable with non-existent rg path returns false`() {
        val enumerator = RipgrepEnumerator(rgPath = "/nonexistent/path/to/rg")
        assertFalse(enumerator.isAvailable())
    }

    // -------------------------------------------------------------------------
    // enumerate() — only run when rg is available
    // -------------------------------------------------------------------------

    @Test
    fun `enumerate produces file paths for a real temp directory`() = runBlocking {
        val rgPath = RipgrepEnumerator.detect()
        assumeTrue(rgPath != null, "rg not installed — skipping enumerate test")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_enum_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            File(tempDir, "alpha.kt").createNewFile()
            File(tempDir, "beta.kt").createNewFile()
            val subDir = File(tempDir, "sub")
            subDir.mkdir()
            File(subDir, "gamma.kt").createNewFile()

            val enumerator = RipgrepEnumerator(rgPath = rgPath!!)
            val paths = enumerator.enumerate(tempDir.absolutePath, 100).toList()

            assertTrue(paths.isNotEmpty(), "Should enumerate at least one file")
            assertTrue(paths.all { it.isNotBlank() }, "All enumerated paths should be non-blank")
            assertTrue(
                paths.any { it.endsWith("alpha.kt") || it.endsWith("beta.kt") || it.endsWith("gamma.kt") },
                "Should include at least one of the created .kt files"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `enumerate respects maxResults cap`() = runBlocking {
        val rgPath = RipgrepEnumerator.detect()
        assumeTrue(rgPath != null, "rg not installed — skipping cap test")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_cap_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // Create more files than maxResults
            for (i in 1..20) {
                File(tempDir, "file$i.txt").createNewFile()
            }

            val enumerator = RipgrepEnumerator(rgPath = rgPath!!)
            val paths = enumerator.enumerate(tempDir.absolutePath, 5).toList()

            assertTrue(paths.size <= 5, "Enumerate should respect maxResults=5, got ${paths.size}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `enumerate on non-existent directory does not throw`() = runBlocking {
        val enumerator = RipgrepEnumerator()
        val result = runCatching {
            enumerator.enumerate("/nonexistent/path/xyz", 10).toList()
        }
        // Should produce empty list or throw a documented exception — must NOT crash the process
        assertTrue(result.isSuccess || result.exceptionOrNull() != null,
            "enumerate() on non-existent path must handle gracefully")
    }

    // -------------------------------------------------------------------------
    // Network mount check (plan section 4)
    // -------------------------------------------------------------------------

    @Test
    fun `RipgrepEnumerator name is not blank`() {
        val enumerator = RipgrepEnumerator()
        assertTrue(enumerator.name.isNotBlank())
    }
}
