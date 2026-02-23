package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.git.GitStatusParser
import ro.faur.explorer.remote.git.GitFileStatus

class GitStatusParserTest {

    @Test
    fun `parses modified file`() {
        val entry = GitStatusParser.parseLine("1 .M N... 100644 100644 100644 abc123 def456 src/Main.kt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.MODIFIED, entry!!.status)
        assertEquals("src/Main.kt", entry.relativePath)
    }

    @Test
    fun `parses staged modified file`() {
        val entry = GitStatusParser.parseLine("1 M. N... 100644 100644 100644 abc123 def456 src/Config.kt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.MODIFIED, entry!!.status)
    }

    @Test
    fun `parses added file`() {
        val entry = GitStatusParser.parseLine("1 A. N... 000000 100644 100644 abc123 def456 src/New.kt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.ADDED, entry!!.status)
        assertEquals("src/New.kt", entry.relativePath)
    }

    @Test
    fun `parses deleted file`() {
        val entry = GitStatusParser.parseLine("1 D. N... 100644 000000 000000 abc123 def456 src/Old.kt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.DELETED, entry!!.status)
    }

    @Test
    fun `parses renamed file`() {
        val entry = GitStatusParser.parseLine("2 R. N... 100644 100644 100644 abc123 def456 R100 new.kt\told.kt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.RENAMED, entry!!.status)
        assertEquals("new.kt", entry.relativePath)
        assertEquals("old.kt", entry.originalPath)
    }

    @Test
    fun `parses untracked file`() {
        val entry = GitStatusParser.parseLine("? untracked-file.txt")
        assertNotNull(entry)
        assertEquals(GitFileStatus.UNTRACKED, entry!!.status)
        assertEquals("untracked-file.txt", entry.relativePath)
    }

    @Test
    fun `parses ignored file`() {
        val entry = GitStatusParser.parseLine("! build/output.jar")
        assertNotNull(entry)
        assertEquals(GitFileStatus.IGNORED, entry!!.status)
    }

    @Test
    fun `returns null for blank line`() {
        assertNull(GitStatusParser.parseLine(""))
        assertNull(GitStatusParser.parseLine("  "))
    }

    @Test
    fun `returns null for unrecognized format`() {
        assertNull(GitStatusParser.parseLine("garbage line"))
    }
}
