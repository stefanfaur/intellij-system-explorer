package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.faur.explorer.settings.QuickOpenSettings
import ro.faur.explorer.util.explorerExceptionHandler
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/**
 * Populates and maintains a LuceneIndexManager from the filesystem.
 *
 * buildIndex: initial full walk using Files.walkFileTree on Dispatchers.IO.
 * startWatcher: incremental update daemon using JDK WatchService.
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

            if (progressCount > 0) onProgress?.invoke(progressCount)
            manager.commit()
            LOG.info("Indexed ${manager.numDocs()} docs for $root")
        }
    }

    /**
     * Watch [root] for filesystem changes and update [manager] incrementally.
     *
     * Handles:
     * - ENTRY_CREATE / ENTRY_MODIFY: re-index the changed file
     * - ENTRY_DELETE: remove from index
     * - OVERFLOW: trigger full re-scan (prevents stale index after bulk operations)
     * - New subdirectories: registered automatically
     *
     * Runs until [scope] is cancelled.
     */
    suspend fun startWatcher(
        root: Path,
        manager: LuceneIndexManager,
        settings: QuickOpenSettings.State,
        scope: CoroutineScope
    ) {
        val allowedExts = settings.luceneExtensionAllowlist
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        withContext(Dispatchers.IO) {
            if (!Files.isDirectory(root)) {
                LOG.warn("Skipping watcher for $root: path does not exist or is not a directory")
                return@withContext
            }
            java.nio.file.FileSystems.getDefault().newWatchService().use { watchService ->
                // Register all existing subdirectories
                Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val dirName = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                        if (dirName in HARD_EXCLUDE_DIRS) return FileVisitResult.SKIP_SUBTREE
                        if (!settings.ripgrepSearchHidden && dirName.startsWith(".") && dir != root) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        try {
                            dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                        } catch (e: IOException) {
                            LOG.warn("Could not register watcher for $dir (skipping): ${e.message}")
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        LOG.warn("Skipping unreadable path during watcher setup: $file (${exc.message})")
                        return FileVisitResult.CONTINUE
                    }
                })

                while (scope.isActive) {
                    val key = withContext(Dispatchers.IO) {
                        watchService.poll(500, TimeUnit.MILLISECONDS)
                    } ?: continue

                    var overflow = false
                    for (event in key.pollEvents()) {
                        when (event.kind()) {
                            OVERFLOW -> {
                                // Events were lost — full re-scan to close the staleness gap
                                overflow = true
                                break
                            }
                            ENTRY_DELETE -> {
                                val changed = (key.watchable() as Path).resolve(event.context() as Path)
                                manager.deleteFile(changed.toString())
                            }
                            ENTRY_CREATE, ENTRY_MODIFY -> {
                                val changed = (key.watchable() as Path).resolve(event.context() as Path)
                                if (Files.isDirectory(changed)) {
                                    // Register new subdirectory so its contents are watched too
                                    val dirName = changed.fileName?.toString()
                                    if (dirName != null && dirName !in HARD_EXCLUDE_DIRS) {
                                        changed.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                                    }
                                } else if (shouldIndex(changed, allowedExts)) {
                                    val content = try {
                                        changed.toFile().readText(Charsets.UTF_8)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    manager.addOrUpdateFile(changed, content, content != null)
                                }
                            }
                        }
                    }

                    if (overflow) {
                        key.reset()
                        scope.launch(Dispatchers.IO + explorerExceptionHandler(null, "Lucene index rebuild on FS overflow for $root")) { buildIndex(root, manager, settings) }
                        continue
                    }

                    manager.commit()
                    if (!key.reset()) break
                }
            }
        }
    }
}
