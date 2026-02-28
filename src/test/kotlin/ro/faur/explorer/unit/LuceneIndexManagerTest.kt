package ro.faur.explorer.unit

import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import java.nio.file.Path
import java.nio.file.Files

class LuceneIndexManagerTest {

    @Test
    fun `test 1 - addOrUpdateFile and searchPaths returns indexed path`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.use {
            val testPath = Path.of("/home/user/readme.md")
            it.addOrUpdateFile(testPath, null, allowContent = false)
            it.commit()
            val results = it.searchPaths("readme", 10)
            assertTrue(results.contains(testPath.toString()), "Expected to find /home/user/readme.md in results: $results")
        }
    }

    @Test
    fun `test 2 - deleteFile removes it from search results`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.use {
            val testPath = Path.of("/home/user/deleteme.txt")
            it.addOrUpdateFile(testPath, null, allowContent = false)
            it.commit()
            it.deleteFile(testPath.toString())
            it.commit()
            val results = it.searchPaths("deleteme", 10)
            assertTrue(results.isEmpty(), "Expected empty results after delete, got: $results")
        }
    }

    @Test
    fun `test 3 - indexDirForRoot produces deterministic path with 16-char hex string`() {
        val root = "/home/user/projects/myproject"
        val path1 = LuceneIndexManager.indexDirForRoot(root)
        val path2 = LuceneIndexManager.indexDirForRoot(root)
        assertEquals(path1, path2, "indexDirForRoot should be deterministic")
        val dirName = path1.fileName.toString()
        assertEquals(16, dirName.length, "Hash prefix should be 16 chars, got: $dirName")
        assertTrue(dirName.matches(Regex("[0-9a-f]+")), "Hash should be hex, got: $dirName")
    }

    @Test
    fun `test 4 - opening a fresh index does not throw`(@TempDir tempDir: Path) {
        val indexPath = tempDir.resolve("fresh-index")
        assertDoesNotThrow {
            LuceneIndexManager(indexPath).use { manager ->
                val testPath = Path.of("/home/user/newfile.txt")
                manager.addOrUpdateFile(testPath, null, allowContent = false)
                manager.commit()
            }
        }
    }

    @Test
    fun `test 5 - corrupt index is silently rebuilt`(@TempDir tempDir: Path) {
        val indexPath = tempDir.resolve("corrupt-index")
        Files.createDirectories(indexPath)
        // Write garbage to simulate corruption
        Files.write(indexPath.resolve("segments_1"), "GARBAGE_CORRUPT_DATA".toByteArray())
        Files.write(indexPath.resolve("write.lock"), ByteArray(0))

        assertDoesNotThrow {
            LuceneIndexManager(indexPath).use { manager ->
                val testPath = Path.of("/home/user/afterrecovery.txt")
                manager.addOrUpdateFile(testPath, null, allowContent = false)
                manager.commit()
                val results = manager.searchPaths("afterrecovery", 10)
                assertTrue(results.contains(testPath.toString()), "Should find file after recovery: $results")
            }
        }
    }

    @Test
    fun `test 6 - searchContent finds document with matching content`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.use {
            val testPath = Path.of("/home/user/document.txt")
            it.addOrUpdateFile(testPath, "hello world this is a test", allowContent = true)
            it.commit()
            val results = it.searchContent("hello", 10)
            val paths = results.map { pair -> pair.first }
            assertTrue(paths.contains(testPath.toString()), "Expected to find doc by content search, got: $paths")
        }
    }

    @Test
    fun `test 7 - searchPaths with blank query returns all indexed paths via MatchAllDocsQuery`() {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        manager.use {
            val paths = listOf(
                Path.of("/home/user/file1.txt"),
                Path.of("/home/user/file2.kt"),
                Path.of("/home/user/file3.md")
            )
            paths.forEach { p -> it.addOrUpdateFile(p, null, allowContent = false) }
            it.commit()
            val results = it.searchPaths("", 100)
            assertEquals(3, results.size, "Blank query should return all indexed docs, got: $results")
            paths.forEach { p ->
                assertTrue(results.contains(p.toString()), "Missing path ${p} in results: $results")
            }
        }
    }
}
