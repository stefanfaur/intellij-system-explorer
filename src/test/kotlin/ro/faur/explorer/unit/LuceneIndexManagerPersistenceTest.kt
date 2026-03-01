package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Automated coverage for human verification item 6:
 * "Open QuickOpen for a large root that previously had an index; close the IDE; reopen —
 *  '[Indexed]' chip reappears quickly because the index was persisted."
 *
 * Tests that NIOFSDirectory-backed indexes survive close/reopen and that the corrupt-recovery
 * path silently rebuilds rather than throwing.
 */
class LuceneIndexManagerPersistenceTest {

    @Test
    fun `index written to disk survives manager close and reopen`(@TempDir indexDir: Path) {
        // Write two docs and close
        val manager1 = LuceneIndexManager(indexDir)
        manager1.addOrUpdateFile(Paths.get("/project/Main.kt"), "fun main() {}", allowContent = true)
        manager1.addOrUpdateFile(Paths.get("/project/Util.kt"), "object Util {}", allowContent = true)
        manager1.commit()
        manager1.close()

        // Reopen at the same path — simulates IDE restart
        val manager2 = LuceneIndexManager(indexDir)
        val results = manager2.searchPaths("", 100)
        manager2.close()

        assertEquals(2, results.size, "Documents should persist across close/reopen, got: $results")
        assertTrue(results.any { it.endsWith("Main.kt") }, "Main.kt missing after reopen: $results")
        assertTrue(results.any { it.endsWith("Util.kt") }, "Util.kt missing after reopen: $results")
    }

    @Test
    fun `corrupt index is silently rebuilt on reopen`(@TempDir indexDir: Path) {
        // Write a garbage file that looks like a Lucene segments file.
        // DirectoryReader.indexExists() checks for a "segments_N" file; if found it tries to parse
        // it and throws CorruptIndexException, which openOrRebuildDirectory catches and rebuilds.
        Files.write(
            indexDir.resolve("segments_1"),
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        )

        // Opening must not throw — rebuild happens silently
        val manager = assertDoesNotThrow<LuceneIndexManager> {
            LuceneIndexManager(indexDir)
        }
        manager.addOrUpdateFile(Paths.get("/project/Test.kt"), "class Test", allowContent = true)
        manager.commit()

        assertEquals(1, manager.numDocs(), "Rebuilt index should accept new documents")
        manager.close()
    }

    @Test
    fun `indexDirForRoot produces the same path deterministically for the same input`() {
        val root = "/home/user/projects/my-project"
        val path1 = LuceneIndexManager.indexDirForRoot(root)
        val path2 = LuceneIndexManager.indexDirForRoot(root)

        assertEquals(path1, path2, "Same root must always produce the same index directory")

        val differentRoot = "/home/user/projects/other-project"
        val path3 = LuceneIndexManager.indexDirForRoot(differentRoot)
        assertNotEquals(path1, path3, "Different roots must produce different index directories")
    }
}
