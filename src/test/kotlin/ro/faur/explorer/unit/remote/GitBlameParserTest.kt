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
        assertEquals("abc123abc123abc123abc123abc123abc123abcd", result[0].commitHash)
        assertEquals("John Doe", result[0].author)
        assertEquals("john@example.com", result[0].authorEmail)
        assertEquals(1700000000L, result[0].timestamp)
        assertEquals(1, result[0].lineNumber)
        assertEquals("line content here", result[0].content)
        assertEquals("Initial commit", result[0].summary)
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
        assertEquals("Alice", result[0].author)
        assertEquals("Bob", result[1].author)
    }

    @Test
    fun `empty output returns empty list`() {
        val result = GitBlameParser.parse("")
        assertTrue(result.isEmpty())
    }
}
