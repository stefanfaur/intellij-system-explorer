package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.git.GitStatusProvider

/**
 * Phase 7 — GitStatusProvider
 *
 * Covers the Automated Verification item:
 *   - GitStatusProvider unit test: returns correct status strings
 *
 * Because we cannot mock ChangeListManager in a light test, we verify:
 *   1. The object can be accessed without throwing
 *   2. getStatus() on a non-VCS path returns "" (not in VCS case)
 *   3. getStatus() never throws regardless of input
 *   4. getCurrentBranch() does not throw
 *   5. All returned status strings are within the allowed set { "M", "?", "✓", "" }
 *
 * Tests fail to compile until GitStatusProvider exists under
 * ro.faur.explorer.quickopen.git.
 */
class Phase7GitStatusProviderTest : BasePlatformTestCase() {

    // -------------------------------------------------------------------------
    // getStatus()
    // -------------------------------------------------------------------------

    fun `test getStatus on non-existent path returns empty string`() {
        val status = GitStatusProvider.getStatus(project, "/this/path/does/not/exist/xyz")
        assertEquals("", status)
    }

    fun `test getStatus does not throw for any input`() {
        val paths = listOf(
            "",
            "/",
            "/tmp",
            "/nonexistent/path",
            "relative/path",
            "/path with spaces/file.kt"
        )
        for (path in paths) {
            val result = runCatching { GitStatusProvider.getStatus(project, path) }
            assertTrue("getStatus() must not throw for path='$path'", result.isSuccess)
        }
    }

    fun `test getStatus returns a value within the allowed set`() {
        val allowed = setOf("M", "?", "✓", "")
        val status = GitStatusProvider.getStatus(project, "/tmp")
        assertTrue(
            "getStatus() returned '$status' which is not in the allowed set $allowed",
            status in allowed
        )
    }

    fun `test getStatus on a path outside VCS returns empty string`() {
        // /tmp is typically not under version control in a test environment
        val status = GitStatusProvider.getStatus(project, System.getProperty("java.io.tmpdir"))
        assertEquals("", status)
    }

    // -------------------------------------------------------------------------
    // getCurrentBranch()
    // -------------------------------------------------------------------------

    fun `test getCurrentBranch does not throw`() {
        val result = runCatching { GitStatusProvider.getCurrentBranch(project) }
        assertTrue("getCurrentBranch() must not throw: ${result.exceptionOrNull()}", result.isSuccess)
    }

    fun `test getCurrentBranch returns null or a non-blank string`() {
        val branch = GitStatusProvider.getCurrentBranch(project)
        // Either null (no VCS root) or a real branch name (non-blank)
        if (branch != null) {
            assertTrue(
                "If branch is non-null it must be non-blank",
                branch.isNotBlank()
            )
        }
    }
}
