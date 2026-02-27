package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.git.GitLogParser

class GitLogParserTest {

    private val validHash = "a".repeat(40)
    private val validHash2 = "b".repeat(40)

    @Test
    fun `parses single well-formed entry`() {
        val lines = listOf("$validHash|John Doe|john@example.com|1700000000|Initial commit")
        val result = GitLogParser.parse(lines)

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals(validHash, entry.hash)
        assertEquals("John Doe", entry.authorName)
        assertEquals("john@example.com", entry.authorEmail)
        assertEquals(1700000000L, entry.timestamp)
        assertEquals("Initial commit", entry.subject)
    }

    @Test
    fun `subject containing pipe characters is preserved`() {
        val lines = listOf("$validHash|Alice|alice@ex.com|1700000000|fix: handle a|b|c edge case")
        val result = GitLogParser.parse(lines)

        assertEquals(1, result.size)
        assertEquals("fix: handle a|b|c edge case", result[0].subject)
    }

    @Test
    fun `multiple entries preserve order`() {
        val lines = listOf(
            "$validHash|Alice|alice@ex.com|1700000000|First commit",
            "$validHash2|Bob|bob@ex.com|1700001000|Second commit",
        )
        val result = GitLogParser.parse(lines)

        assertEquals(2, result.size)
        assertEquals("First commit", result[0].subject)
        assertEquals("Second commit", result[1].subject)
    }

    @Test
    fun `empty output returns empty list`() {
        val result = GitLogParser.parse(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank lines are skipped`() {
        val lines = listOf("", "  ", "$validHash|A|a@b.com|1700000000|msg")
        val result = GitLogParser.parse(lines)
        assertEquals(1, result.size)
    }

    @Test
    fun `malformed lines with too few fields are skipped`() {
        val lines = listOf(
            "$validHash|Alice|alice@ex.com",  // only 3 fields
            "$validHash|Bob|bob@ex.com|1700000000|Valid",
        )
        val result = GitLogParser.parse(lines)
        assertEquals(1, result.size)
        assertEquals("Valid", result[0].subject)
    }

    @Test
    fun `non-40-char hash is skipped`() {
        val lines = listOf("shorthash|Alice|alice@ex.com|1700000000|Bad hash")
        val result = GitLogParser.parse(lines)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unparseable epoch is skipped`() {
        val lines = listOf("$validHash|Alice|alice@ex.com|not-a-number|Bad epoch")
        val result = GitLogParser.parse(lines)
        assertTrue(result.isEmpty())
    }
}
