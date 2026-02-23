package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.git.RemoteGitCommandExecutor

class ShellUtilsTest {

    @Test
    fun `escapes simple string`() {
        assertEquals("'hello'", RemoteGitCommandExecutor.shellEscape("hello"))
    }

    @Test
    fun `escapes string with single quotes`() {
        assertEquals("'it'\\''s'", RemoteGitCommandExecutor.shellEscape("it's"))
    }

    @Test
    fun `escapes empty string`() {
        assertEquals("''", RemoteGitCommandExecutor.shellEscape(""))
    }

    @Test
    fun `escapes path with spaces`() {
        assertEquals("'/var/my folder/file.txt'", RemoteGitCommandExecutor.shellEscape("/var/my folder/file.txt"))
    }

    @Test
    fun `escapes string with special characters`() {
        val escaped = RemoteGitCommandExecutor.shellEscape("hello; rm -rf /")
        assertEquals("'hello; rm -rf /'", escaped)
    }

    @Test
    fun `escapes string with backticks`() {
        val escaped = RemoteGitCommandExecutor.shellEscape("echo `whoami`")
        assertEquals("'echo `whoami`'", escaped)
    }

    @Test
    fun `escapes string with dollar sign`() {
        val escaped = RemoteGitCommandExecutor.shellEscape("\$HOME")
        assertEquals("'\$HOME'", escaped)
    }
}
