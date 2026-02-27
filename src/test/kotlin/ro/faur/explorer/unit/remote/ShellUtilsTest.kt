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

    @Test
    fun `safe-directory flag is correctly escaped for a normal path`() {
        // Verifies the -c safe.directory=<path> flag that bypasses git 2.35.2+ dubious-
        // ownership checks. The value is shell-escaped as a single token so git receives
        // the key=value pair intact.
        val path = "/home/tomcat/PkOneConfiguration"
        val escaped = RemoteGitCommandExecutor.shellEscape("safe.directory=$path")
        assertEquals("'safe.directory=/home/tomcat/PkOneConfiguration'", escaped)
    }

    @Test
    fun `safe-directory flag handles path with spaces`() {
        val path = "/home/my user/my repo"
        val escaped = RemoteGitCommandExecutor.shellEscape("safe.directory=$path")
        assertEquals("'safe.directory=/home/my user/my repo'", escaped)
    }
}
