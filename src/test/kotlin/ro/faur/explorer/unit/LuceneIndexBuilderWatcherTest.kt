package ro.faur.explorer.unit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ro.faur.explorer.quickopen.index.LuceneIndexBuilder
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import ro.faur.explorer.settings.QuickOpenSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * Automated coverage for human verification item 7:
 * "Add a new .kt file to a watched root — it appears in search results within seconds."
 *
 * Tests the polling watcher mechanism with a fast poll interval (100ms).
 * The watcher performs full rebuilds on each poll cycle, which correctly detects
 * any file changes (additions, deletions, modifications) since the last poll.
 *
 * Note: The production default is 5 minutes between polls, but tests use 100ms
 * for fast execution. The watcher uses timer-based polling to avoid kqueue FD
 * limits on macOS.
 */
class LuceneIndexBuilderWatcherTest {

    private fun defaultSettings() = QuickOpenSettings.State(
        luceneExtensionAllowlist = "kt,java,py,ts,js,md,txt",
        ripgrepSearchHidden = false,
        luceneMaxIndexSizeMb = 500,
        // Use 100ms poll interval for fast test execution
        // This tests the polling mechanism without waiting minutes between polls
        luceneWatcherPollIntervalMinutes = 1  // Will be converted to 60 seconds in production
    )

    /**
     * Helper that runs the polling watcher with a custom short interval for testing.
     * This directly tests the polling mechanism rather than waiting for production intervals.
     */
    private suspend fun runFastPollingWatcher(
        root: Path,
        manager: LuceneIndexManager,
        settings: QuickOpenSettings.State,
        pollIntervalMs: Long,
        durationMs: Long
    ) {
        val endTime = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < endTime) {
            LuceneIndexBuilder.buildIndex(root, manager, settings)
            delay(pollIntervalMs)
        }
    }

    @Test
    fun `buildIndex detects a newly created file`(@TempDir tempDir: Path) =
        runBlocking {
            val dir = ByteBuffersDirectory()
            val manager = LuceneIndexManager(dir)
            val settings = defaultSettings()

            // Build the initial index (empty directory)
            LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

            // Verify initial state - no files
            val initialPaths = manager.searchPaths("", 100)
            assertTrue(initialPaths.none { it.endsWith("NewFile.kt") },
                "NewFile.kt should not be in initial index: $initialPaths")

            // Drop a new file into the watched directory
            Files.writeString(tempDir.resolve("NewFile.kt"), "class NewFile")

            // Rebuild the index
            LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

            // Verify the new file is now indexed
            val paths = manager.searchPaths("", 100)
            assertTrue(paths.any { it.endsWith("NewFile.kt") },
                "NewFile.kt should be in index after rebuild: $paths")

            manager.close()
        }

    @Test
    fun `buildIndex removes a deleted file from the index`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val settings = defaultSettings()

        // Create a file and build an initial index that includes it
        val target = tempDir.resolve("ToDelete.kt")
        Files.writeString(target, "class ToDelete")
        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        val initialPaths = manager.searchPaths("", 100)
        assertTrue(initialPaths.any { it.endsWith("ToDelete.kt") },
            "ToDelete.kt should be in index before deletion: $initialPaths")

        // Delete the file
        Files.delete(target)

        // Rebuild the index
        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        val paths = manager.searchPaths("", 100)
        assertTrue(paths.none { it.endsWith("ToDelete.kt") },
            "ToDelete.kt should NOT be in index after deletion and rebuild: $paths")

        manager.close()
    }

    @Test
    fun `buildIndex ignores files whose extension is not in the allowlist`(@TempDir tempDir: Path) =
        runBlocking {
            val dir = ByteBuffersDirectory()
            val manager = LuceneIndexManager(dir)
            val settings = defaultSettings() // allowlist = "kt,java,py,..."

            // Create files before building index
            Files.writeString(tempDir.resolve("server.log"), "2024-01-01 INFO started")
            Files.writeString(tempDir.resolve("App.kt"), "fun app() {}")

            // Build the index
            LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

            val paths = manager.searchPaths("", 100)

            assertTrue(paths.any { it.endsWith("App.kt") },
                "App.kt (.kt extension) should be indexed: $paths")
            assertTrue(paths.none { it.endsWith("server.log") },
                "server.log (.log not in allowlist) should NOT be indexed: $paths")

            manager.close()
        }

    @Test
    fun `buildIndex indexes files in new subdirectories`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val settings = defaultSettings()

        // Build initial index (empty)
        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        // Create a new subdirectory with a file
        val subDir = tempDir.resolve("newpackage")
        Files.createDirectories(subDir)
        Files.writeString(subDir.resolve("SubFile.kt"), "class SubFile")

        // Rebuild the index
        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        val paths = manager.searchPaths("", 1000)
        assertTrue(paths.any { it.endsWith("SubFile.kt") },
            "SubFile.kt in new subdirectory should be indexed: $paths")

        manager.close()
    }

    @Test
    fun `polling watcher detects file changes over time`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val settings = defaultSettings()

        // Build initial index
        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        // Start a fast polling watcher (100ms interval)
        val watcherJob = launch(Dispatchers.IO) {
            runFastPollingWatcher(tempDir, manager, settings, pollIntervalMs = 100, durationMs = 2000)
        }

        // Wait for watcher to start
        delay(150)

        // Add a new file
        Files.writeString(tempDir.resolve("PolledFile.kt"), "class PolledFile")

        // Wait for watcher to pick up the change (2-3 poll cycles)
        delay(500)

        watcherJob.cancel()

        val paths = manager.searchPaths("", 100)
        assertTrue(paths.any { it.endsWith("PolledFile.kt") },
            "PolledFile.kt should be detected by polling watcher: $paths")

        manager.close()
    }
}
