package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.git.GitBlameParser

class GitBlameParserTest {

    @Test
    fun `parses single blame entry`() {
        val output = """
            abc123abc123abc123abc123abc123abc123abcd 1 1 1
            author John Doe
            author-mail <john@example.com>
            author-time 1700000000
            summary Initial commit
            	line content here
        """.trimIndent()

        val result = GitBlameParser.parse(output)
        assertEquals(1, result.size)
        val line = result[0]!!
        assertEquals("abc123abc123abc123abc123abc123abc123abcd", line.commitHash)
        assertEquals("John Doe", line.author)
        assertEquals("john@example.com", line.authorEmail)
        assertEquals(1700000000L, line.timestamp)
        assertEquals(1, line.lineNumber)
        assertEquals("line content here", line.content)
        assertEquals("Initial commit", line.summary)
    }

    @Test
    fun `parses multiple blame entries`() {
        val output = """
            abc123abc123abc123abc123abc123abc123abcd 1 1 1
            author Alice
            author-mail <alice@example.com>
            author-time 1700000000
            summary First commit
            	line 1
            def456def456def456def456def456def456defg 2 2 1
            author Bob
            author-mail <bob@example.com>
            author-time 1700001000
            summary Second commit
            	line 2
        """.trimIndent()

        val result = GitBlameParser.parse(output)
        assertEquals(2, result.size)
        assertEquals("Alice", result[0]!!.author)
        assertEquals("Bob", result[1]!!.author)
    }

    @Test
    fun `empty output returns empty list`() {
        val result = GitBlameParser.parse("")
        assertTrue(result.isEmpty())
    }
}
