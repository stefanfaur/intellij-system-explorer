package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ro.faur.explorer.settings.QuickOpenSettings
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Populates and maintains a LuceneIndexManager from the filesystem.
 *
 * buildIndex: initial full walk using Files.walkFileTree on Dispatchers.IO.
 * startPollingWatcher: periodic re-index daemon (no FD usage).
 * countFiles: quick count helper for threshold decisions.
 * shouldIndex: filter predicate for extension, size, and binary content.
 */
object LuceneIndexBuilder {

    private val LOG = Logger.getInstance(LuceneIndexBuilder::class.java)

    private val HARD_EXCLUDE_DIRS = setOf(
        "node_modules", ".git", "build", "target", ".gradle", ".idea",
        "__pycache__", ".DS_Store", "dist", "out"
    )

    /**
     * Count all regular files under root, skipping HARD_EXCLUDE_DIRS.
     * Stops counting once count exceeds [stopAt] (returns stopAt + 1).
     * Returns -1 on error. Returns stopAt + 1 on timeout (treat as "large").
     */
    fun countFiles(root: Path, timeoutMs: Long = 5_000, stopAt: Int = Int.MAX_VALUE): Int {
        class StopCounting : Exception()
        return try {
            var result = -1
            val thread = Thread {
                result = try {
                    var count = 0
                    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                            return if (name in HARD_EXCLUDE_DIRS) FileVisitResult.SKIP_SUBTREE
                                   else FileVisitResult.CONTINUE
                        }
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            count++
                            if (count > stopAt) throw StopCounting()
                            return FileVisitResult.CONTINUE
                        }
                        override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult =
                            FileVisitResult.CONTINUE
                    })
                    count
                } catch (_: StopCounting) {
                    stopAt + 1
                } catch (_: Exception) {
                    -1
                }
            }
            thread.isDaemon = true
            thread.start()
            thread.join(timeoutMs)
            if (thread.isAlive) {
                thread.interrupt()
                stopAt + 1  // timeout = treat as "large"
            } else {
                result
            }
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Determine whether a file should be indexed.
     *
     * Rules (in order):
     * 1. Extension must be in allowedExts (lowercase, no dot)
     * 2. File size must not exceed maxSizeBytes (default 1 MB)
     * 3. File must not start with a null byte (binary detection)
     */
    fun shouldIndex(file: Path, allowedExts: Set<String>, maxSizeBytes: Long = 1_048_576): Boolean {
        val name = file.fileName?.toString() ?: return false
        val dotIdx = name.lastIndexOf('.')
        val ext = if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
        if (ext !in allowedExts) return false

        val size = try { Files.size(file) } catch (e: Exception) { return false }
        if (size > maxSizeBytes) return false

        // Binary detection: read first 8 bytes; if any byte is 0x00, treat as binary
        if (size > 0) {
            try {
                Files.newInputStream(file).use { stream ->
                    val bytes = ByteArray(8)
                    val read = stream.read(bytes)
                    for (i in 0 until read) {
                        if (bytes[i] == 0x00.toByte()) return false
                    }
                }
            } catch (e: Exception) {
                return false
            }
        }

        return true
    }

    /**
     * Walk [root] and index all eligible files into [manager].
     * Also removes any indexed files that no longer exist on disk.
     *
     * Must be called on Dispatchers.IO (suspend).
     */
    suspend fun buildIndex(
        root: Path,
        manager: LuceneIndexManager,
        settings: QuickOpenSettings.State,
        onProgress: ((Int) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            if (!Files.isDirectory(root)) {
                LOG.warn("Skipping index build for $root: path does not exist or is not a directory")
                return@withContext
            }
            val allowedExts = settings.luceneExtensionAllowlist
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val totalContentCapBytes = settings.luceneMaxIndexSizeMb.toLong() * 1024L * 1024L
            var totalIndexedBytes = 0L
            var contentCapReached = false
            var progressCount = 0
            
            // Track all paths visited during this walk to detect deletions
            val visitedPaths = mutableSetOf<String>()

            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val dirName = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                    if (dirName in HARD_EXCLUDE_DIRS) return FileVisitResult.SKIP_SUBTREE
                    if (!settings.ripgrepSearchHidden && dirName.startsWith(".") && dir != root) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!shouldIndex(file, allowedExts)) return FileVisitResult.CONTINUE

                    // Track this path
                    visitedPaths.add(file.toString())

                    val fileSize = attrs.size()
                    if (!contentCapReached && totalIndexedBytes + fileSize > totalContentCapBytes) {
                        contentCapReached = true
                    }

                    val content = if (!contentCapReached) {
                        try { file.toFile().readText(Charsets.UTF_8) } catch (e: Exception) { null }
                    } else {
                        null
                    }

                    manager.addOrUpdateFile(file, content, !contentCapReached)

                    if (!contentCapReached) {
                        totalIndexedBytes += fileSize
                    }

                    progressCount++
                    if (progressCount % 100 == 0) onProgress?.invoke(progressCount)

                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    LOG.warn("Skipping unreadable path during index build: $file (${exc.message})")
                    return FileVisitResult.CONTINUE
                }
            })
            
            // Remove indexed files that no longer exist on disk
            val indexedPaths = manager.searchPaths("", Int.MAX_VALUE)
            for (indexedPath in indexedPaths) {
                // Only delete paths that are under the root and were not visited
                if (indexedPath.startsWith(root.toString()) && indexedPath !in visitedPaths) {
                    manager.deleteFile(indexedPath)
                    LOG.debug("Removed deleted file from index: $indexedPath")
                }
            }

            if (progressCount > 0) onProgress?.invoke(progressCount)
            manager.commit()
            LOG.info("Indexed ${manager.numDocs()} docs for $root")
        }
    }

    /**
     * Periodically re-indexes [root] by calling [buildIndex] on a configurable timer.
     *
     * Uses no file descriptors — avoids the kqueue/inotify FD drain caused by registering
     * every subdirectory with JDK WatchService (which consumed one FD per directory on macOS).
     * The poll interval is read from [settings.luceneWatcherPollIntervalMinutes].
     *
     * Runs until [scope] is cancelled.
     */
    suspend fun startPollingWatcher(
        root: Path,
        manager: LuceneIndexManager,
        settings: QuickOpenSettings.State,
        scope: CoroutineScope
    ) {
        val intervalMs = settings.luceneWatcherPollIntervalMinutes * 60_000L
        withContext(Dispatchers.IO) {
            while (scope.isActive) {
                delay(intervalMs)
                if (!scope.isActive) break
                if (!Files.isDirectory(root)) {
                    LOG.warn("Stopping index poller for $root: path no longer exists or is not a directory")
                    break
                }
                buildIndex(root, manager, settings)
            }
        }
    }
}
