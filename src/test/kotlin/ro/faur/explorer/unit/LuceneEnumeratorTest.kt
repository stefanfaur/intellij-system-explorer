package ro.faur.explorer.unit

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.LuceneEnumerator
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import java.nio.file.Paths

/**
 * Unit tests for LuceneEnumerator — the drop-in EnumeratorBackend backed by LuceneIndexManager.
 *
 * Covers the contract: enumerate() calls searchPaths("", maxResults) which triggers
 * MatchAllDocsQuery inside LuceneIndexManager, emitting all indexed paths as a Flow.
 */
class LuceneEnumeratorTest {

    @Test
    fun `enumerate emits all indexed paths via MatchAllDocsQuery`() = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.addOrUpdateFile(Paths.get("/project/Main.kt"), null, allowContent = false)
        manager.addOrUpdateFile(Paths.get("/project/Util.kt"), null, allowContent = false)
        manager.addOrUpdateFile(Paths.get("/project/Model.kt"), null, allowContent = false)
        manager.commit()

        val enumerator = LuceneEnumerator(manager)
        val paths = enumerator.enumerate("/project", 100).toList()

        assertEquals(3, paths.size, "Should enumerate all 3 indexed paths: $paths")
        assertTrue(paths.any { it.endsWith("Main.kt") }, "Main.kt missing from enumeration: $paths")
        assertTrue(paths.any { it.endsWith("Util.kt") }, "Util.kt missing from enumeration: $paths")
        assertTrue(paths.any { it.endsWith("Model.kt") }, "Model.kt missing from enumeration: $paths")

        manager.close()
    }

    @Test
    fun `enumerate returns empty flow when index is empty`() = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)

        val enumerator = LuceneEnumerator(manager)
        val paths = enumerator.enumerate("/empty", 100).toList()

        assertTrue(paths.isEmpty(), "Empty index should produce empty flow")

        manager.close()
    }

    @Test
    fun `isAvailable always returns true`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val enumerator = LuceneEnumerator(manager)

        assertTrue(enumerator.isAvailable(),
            "LuceneEnumerator.isAvailable() should always be true (manager handles corrupt-open internally)")

        manager.close()
    }
}
