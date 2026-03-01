package ro.faur.explorer.unit

import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.LuceneContentSearch
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import java.nio.file.Paths

/**
 * Automated coverage for human verification item 4:
 * "Use content search (/: prefix) on a root that has a Lucene index — results appear from both
 *  Lucene (score 2.0, has snippet) and ripgrep (score 1.0); no duplicate paths; Lucene first."
 *
 * Tests the LuceneContentSearch wrapper: blank guard, exception safety, result pass-through,
 * and maxResults cap. The deduplication/ordering logic lives in QuickOpenPanel.performContentSearch
 * and is UI-level; it is covered here for the Lucene side only.
 */
class LuceneContentSearchTest {

    @Test
    fun `search returns empty list for blank pattern`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val searcher = LuceneContentSearch(manager)

        assertTrue(searcher.search("").isEmpty(), "Empty pattern should return empty list")
        assertTrue(searcher.search("   ").isEmpty(), "Blank-only pattern should return empty list")

        manager.close()
    }

    @Test
    fun `search returns empty list when manager throws and does not propagate the exception`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.close() // close before search to force an internal exception

        val searcher = LuceneContentSearch(manager)

        assertDoesNotThrow {
            val result = searcher.search("anything")
            assertTrue(result.isEmpty(), "Closed manager should yield empty list, not throw")
        }
    }

    @Test
    fun `search returns path-snippet pairs for matching content`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.addOrUpdateFile(
            Paths.get("/src/Main.kt"),
            "fun main() { println(\"hello world\") }",
            allowContent = true
        )
        manager.addOrUpdateFile(
            Paths.get("/src/Util.kt"),
            "object Util { fun noop() {} }",
            allowContent = true
        )
        manager.commit()

        val searcher = LuceneContentSearch(manager)
        val results = searcher.search("hello")

        assertFalse(results.isEmpty(), "Search for 'hello' should return at least one result")
        assertTrue(results.any { it.first.endsWith("Main.kt") },
            "Main.kt (contains 'hello world') should appear in results: $results")
        assertTrue(results.none { it.first.endsWith("Util.kt") },
            "Util.kt (no 'hello') should not appear in results: $results")

        manager.close()
    }

    @Test
    fun `search respects maxResults cap`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        for (i in 1..20) {
            manager.addOrUpdateFile(
                Paths.get("/src/File$i.kt"),
                "fun function$i() { println(\"target\") }",
                allowContent = true
            )
        }
        manager.commit()

        val searcher = LuceneContentSearch(manager)
        val results = searcher.search("target", maxResults = 5)

        assertTrue(results.size <= 5,
            "Results should be capped at maxResults=5, got ${results.size}")

        manager.close()
    }
}
