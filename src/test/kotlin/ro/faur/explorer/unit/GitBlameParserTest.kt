package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.remote.git.GitBlameParser

class GitBlameParserTest {
    private val samplePorcelain = """
        abc1230000000000000000000000000000000000 1 1 1
        author Test Author
        author-mail <test@example.com>
        author-time 1700000000
        author-tz +0000
        committer Test Author
        committer-mail <test@example.com>
        committer-time 1700000000
        committer-tz +0000
        summary Initial commit
        filename src/Main.kt
        	first line
        abc1230000000000000000000000000000000000 3 2 1
        author Test Author
        author-mail <test@example.com>
        author-time 1700000000
        author-tz +0000
        committer Test Author
        committer-mail <test@example.com>
        committer-time 1700000000
        committer-tz +0000
        summary Initial commit
        filename src/Main.kt
        	third line
    """.trimIndent()

    @Test fun `parses line numbers correctly into 0-based map`() {
        val result = GitBlameParser.parse(samplePorcelain)
        assertNotNull(result[0], "git line 1 should map to index 0")
        assertNull(result[1], "index 1 should be absent (gap at git line 2)")
        assertNotNull(result[2], "git line 3 should map to index 2, not 1")
    }

    @Test fun `parsed blame line has correct hash`() {
        val result = GitBlameParser.parse(samplePorcelain)
        assertEquals("abc1230000000000000000000000000000000000", result[0]?.commitHash)
    }

    @Test fun `all-zeros hash is marked as uncommitted`() {
        val allZeros = "0" .repeat(40)
        val porcelain = """
            $allZeros 1 1 1
            author Not Committed Yet
            author-mail <not.committed.yet>
            author-time 0
            author-tz +0000
            committer Not Committed Yet
            committer-mail <not.committed.yet>
            committer-time 0
            committer-tz +0000
            summary Version of Main.kt
            filename src/Main.kt
            	content line
        """.trimIndent()
        val result = GitBlameParser.parse(porcelain)
        assertTrue(result[0]?.isUncommitted == true, "all-zeros hash should be isUncommitted=true")
    }
}
