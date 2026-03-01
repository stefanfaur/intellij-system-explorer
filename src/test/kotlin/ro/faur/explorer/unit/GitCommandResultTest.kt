package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.exec.GitCommandResult

class GitCommandResultTest {

    // --- isSuccess ---

    @Test
    fun `exitCode 0 means isSuccess true`() {
        val result = GitCommandResult(exitCode = 0, stdout = "", stderr = "")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `exitCode 1 means isSuccess false`() {
        val result = GitCommandResult(exitCode = 1, stdout = "", stderr = "")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `exitCode 128 means isSuccess false`() {
        val result = GitCommandResult(exitCode = 128, stdout = "", stderr = "")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `exitCode negative 1 means isSuccess false`() {
        val result = GitCommandResult(exitCode = -1, stdout = "", stderr = "")
        assertFalse(result.isSuccess)
    }

    // --- stdoutLines ---

    @Test
    fun `stdoutLines on empty stdout returns empty list`() {
        val result = GitCommandResult(exitCode = 0, stdout = "", stderr = "")
        assertEquals(emptyList<String>(), result.stdoutLines)
    }

    @Test
    fun `stdoutLines filters out blank lines`() {
        val result = GitCommandResult(exitCode = 0, stdout = "\n\n", stderr = "")
        assertEquals(emptyList<String>(), result.stdoutLines)
    }

    @Test
    fun `stdoutLines returns non-empty lines`() {
        val result = GitCommandResult(exitCode = 0, stdout = "line1\nline2\nline3", stderr = "")
        assertEquals(listOf("line1", "line2", "line3"), result.stdoutLines)
    }

    @Test
    fun `stdoutLines filters empty lines in the middle of multiline stdout`() {
        val result = GitCommandResult(exitCode = 0, stdout = "first\n\nsecond\n\nthird", stderr = "")
        assertEquals(listOf("first", "second", "third"), result.stdoutLines)
    }

    @Test
    fun `stdoutLines filters trailing newline`() {
        val result = GitCommandResult(exitCode = 0, stdout = "main\ndevelop\n", stderr = "")
        assertEquals(listOf("main", "develop"), result.stdoutLines)
    }

    @Test
    fun `stdoutLines returns single line without trailing newline`() {
        val result = GitCommandResult(exitCode = 0, stdout = "main", stderr = "")
        assertEquals(listOf("main"), result.stdoutLines)
    }

    // --- stderr accessible ---

    @Test
    fun `stderr is accessible and preserved`() {
        val result = GitCommandResult(exitCode = 1, stdout = "", stderr = "fatal: not a git repository")
        assertEquals("fatal: not a git repository", result.stderr)
    }

    @Test
    fun `stderr is empty string when no error output`() {
        val result = GitCommandResult(exitCode = 0, stdout = "ok", stderr = "")
        assertEquals("", result.stderr)
    }

    // --- Data class equality ---

    @Test
    fun `two instances with same values are equal`() {
        val a = GitCommandResult(exitCode = 0, stdout = "hello", stderr = "")
        val b = GitCommandResult(exitCode = 0, stdout = "hello", stderr = "")
        assertEquals(a, b)
    }

    @Test
    fun `two instances with different exitCode are not equal`() {
        val a = GitCommandResult(exitCode = 0, stdout = "hello", stderr = "")
        val b = GitCommandResult(exitCode = 1, stdout = "hello", stderr = "")
        assertFalse(a == b)
    }

    @Test
    fun `two instances with different stdout are not equal`() {
        val a = GitCommandResult(exitCode = 0, stdout = "hello", stderr = "")
        val b = GitCommandResult(exitCode = 0, stdout = "world", stderr = "")
        assertFalse(a == b)
    }

    @Test
    fun `data class copy produces equal instance`() {
        val original = GitCommandResult(exitCode = 0, stdout = "output", stderr = "err")
        val copy = original.copy()
        assertEquals(original, copy)
    }
}
