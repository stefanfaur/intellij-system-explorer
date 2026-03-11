package ro.faur.explorer.unit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * WatchService on macOS uses a polling implementation (~2 second sensitivity). Tests use
 * a 10-second polling window to remain reliable cross-platform.
 *
 * Also covers: ENTRY_DELETE removal, extension-filtered exclusion, and new subdir registration.
 */
class LuceneIndexBuilderWatcherTest {

    private fun defaultSettings() = QuickOpenSettings.State(
        luceneExtensionAllowlist = "kt,java,py,ts,js,md,txt",
        ripgrepSearchHidden = false,
        luceneMaxIndexSizeMb = 500
    )

    /** Polls [predicate] every 500 ms for up to [maxWaitMs] ms. Returns true if predicate passes. */
    private suspend fun waitUntil(maxWaitMs: Long = 10_000L, predicate: () -> Boolean): Boolean {
        val step = 500L
        var elapsed = 0L
        while (elapsed < maxWaitMs) {
            if (predicate()) return true
            delay(step)
            elapsed += step
        }
        return predicate()
    }

    @Test
    fun `startPollingWatcher detects a newly created file and adds it to the index`(@TempDir tempDir: Path) =
        runBlocking {
            val dir = ByteBuffersDirectory()
            val manager = LuceneIndexManager(dir)
            val settings = defaultSettings()

            // Build the initial index (empty directory)
            LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

            val watcherJob = launch(Dispatchers.IO) {
                LuceneIndexBuilder.startPollingWatcher(tempDir, manager, settings, this)
            }

            delay(300) // Let watcher register directories

            // Drop a new file into the watched directory
            Files.writeString(tempDir.resolve("NewFile.kt"), "class NewFile")

            val found = waitUntil {
                manager.searchPaths("", 1000).any { it.endsWith("NewFile.kt") }
            }

            watcherJob.cancel()
            manager.close()

            assertTrue(found, "WatchService should detect and index NewFile.kt within 10 seconds")
        }

    @Test
    fun `startPollingWatcher removes a deleted file from the index`(@TempDir tempDir: Path) = runBlocking {
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

        val watcherJob = launch(Dispatchers.IO) {
            LuceneIndexBuilder.startPollingWatcher(tempDir, manager, settings, this)
        }

        delay(300) // Let watcher register

        Files.delete(target)

        val removed = waitUntil {
            manager.searchPaths("", 100).none { it.endsWith("ToDelete.kt") }
        }

        watcherJob.cancel()
        manager.close()

        assertTrue(removed, "WatchService should remove ToDelete.kt from index within 10 seconds")
    }

    @Test
    fun `startPollingWatcher ignores files whose extension is not in the allowlist`(@TempDir tempDir: Path) =
        runBlocking {
            val dir = ByteBuffersDirectory()
            val manager = LuceneIndexManager(dir)
            val settings = defaultSettings() // allowlist = "kt,java,py,..."

            LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

            val watcherJob = launch(Dispatchers.IO) {
                LuceneIndexBuilder.startPollingWatcher(tempDir, manager, settings, this)
            }

            delay(300)

            // .log is not in the allowlist — should be ignored
            Files.writeString(tempDir.resolve("server.log"), "2024-01-01 INFO started")
            // .kt is in the allowlist — should be indexed
            Files.writeString(tempDir.resolve("App.kt"), "fun app() {}")

            val ktIndexed = waitUntil {
                manager.searchPaths("", 100).any { it.endsWith("App.kt") }
            }

            watcherJob.cancel()
            val allPaths = manager.searchPaths("", 100)
            manager.close()

            assertTrue(ktIndexed, "App.kt (.kt extension) should be indexed by WatchService")
            assertTrue(allPaths.none { it.endsWith("server.log") },
                "server.log (.log not in allowlist) should NOT be indexed: $allPaths")
        }

    @Test
    fun `startPollingWatcher registers a new subdirectory and watches files created inside it`(
        @TempDir tempDir: Path
    ) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        val settings = defaultSettings()

        LuceneIndexBuilder.buildIndex(tempDir, manager, settings)

        val watcherJob = launch(Dispatchers.IO) {
            LuceneIndexBuilder.startPollingWatcher(tempDir, manager, settings, this)
        }

        delay(300)

        // Create a new subdirectory AFTER the watcher started — it should be auto-registered
        val subDir = tempDir.resolve("newpackage")
        Files.createDirectories(subDir)
        delay(800) // Give watcher time to register the new directory

        // Now create a file inside the new subdirectory
        Files.writeString(subDir.resolve("SubFile.kt"), "class SubFile")

        val found = waitUntil {
            manager.searchPaths("", 1000).any { it.endsWith("SubFile.kt") }
        }

        watcherJob.cancel()
        manager.close()

        assertTrue(found,
            "WatchService should auto-register new subdirectory and detect files created within it")
    }
}
