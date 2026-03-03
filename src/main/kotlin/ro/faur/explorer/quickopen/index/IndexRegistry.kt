package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ro.faur.explorer.settings.QuickOpenSettings
import ro.faur.explorer.util.explorerExceptionHandler
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

data class IndexStats(
    val docCount: Int,
    val diskSizeBytes: Long,
    val lastBuiltMs: Long,
)

object IndexRegistry {
    private val managers = ConcurrentHashMap<String, LuceneIndexManager>()
    private val lastAccessed = ConcurrentHashMap<String, Long>()
    private val buildingRoots = ConcurrentHashMap.newKeySet<String>()  // roots currently being built
    private val lastBuiltMs = ConcurrentHashMap<String, Long>()
    private val buildProgress = ConcurrentHashMap<String, Int>()
    private val registryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val LOG = Logger.getInstance(IndexRegistry::class.java)

    /**
     * Returns the LuceneIndexManager for the given root if the index is ready,
     * or null if the index is still building (or the root is below the threshold).
     * As a side effect, triggers a background build+watch if this is the first call for this root.
     */
    fun getOrBuild(
        root: String,
        settings: QuickOpenSettings.State,
        onIndexReady: () -> Unit
    ): LuceneIndexManager? {
        lastAccessed[root] = System.currentTimeMillis()
        evictStaleIndexes(settings)

        val existing = managers[root]
        if (existing != null) return existing

        if (!buildingRoots.add(root)) return null  // already building

        registryScope.launch(Dispatchers.IO + explorerExceptionHandler(null, "Lucene index build for $root")) {
            try {
                val rootPath = Paths.get(root)
                val indexPath = LuceneIndexManager.indexDirForRoot(root)
                Files.createDirectories(indexPath)
                val manager = LuceneIndexManager(indexPath)
                buildProgress[root] = 0
                LuceneIndexBuilder.buildIndex(rootPath, manager, settings) { count ->
                    buildProgress[root] = count
                }
                managers[root] = manager
                lastBuiltMs[root] = System.currentTimeMillis()
                onIndexReady()  // callback so QuickOpenPanel can switch to LuceneEnumerator
                // Start the watcher after build completes
                LuceneIndexBuilder.startWatcher(rootPath, manager, settings, registryScope)
            } catch (e: Exception) {
                // explorerExceptionHandler already notified; just log here
                LOG.warn("Failed to build Lucene index for $root", e)
            } finally {
                buildProgress.remove(root)
                buildingRoots.remove(root)
            }
        }
        return null  // index not ready yet; caller falls back to live ripgrep
    }

    fun isBuilding(root: String): Boolean = root in buildingRoots

    fun getManager(root: String): LuceneIndexManager? = managers[root]

    fun getBuildProgress(root: String): Int = buildProgress[root] ?: 0

    fun listIndexedRoots(): Map<String, IndexStats> {
        return managers.entries.associate { (root, manager) ->
            val diskSize = try {
                val dir = LuceneIndexManager.indexDirForRoot(root)
                if (java.nio.file.Files.exists(dir))
                    java.nio.file.Files.walk(dir).mapToLong { f ->
                        try { java.nio.file.Files.size(f) } catch (_: Exception) { 0L }
                    }.sum()
                else 0L
            } catch (_: Exception) { 0L }
            root to IndexStats(
                docCount = try { manager.numDocs() } catch (_: Exception) { -1 },
                diskSizeBytes = diskSize,
                lastBuiltMs = lastBuiltMs[root] ?: 0L,
            )
        }
    }

    fun rebuild(root: String, settings: QuickOpenSettings.State, onIndexReady: () -> Unit) {
        val existing = managers.remove(root)
        existing?.close()
        lastBuiltMs.remove(root)
        buildProgress.remove(root)
        buildingRoots.remove(root)
        try {
            LuceneIndexManager.indexDirForRoot(root).toFile().deleteRecursively()
        } catch (_: Exception) {}
        getOrBuild(root, settings, onIndexReady)
    }

    /** Test-only: inject a pre-built manager without triggering a real build. */
    internal fun registerForTest(root: String, manager: LuceneIndexManager, builtAtMs: Long = System.currentTimeMillis()) {
        managers[root] = manager
        lastBuiltMs[root] = builtAtMs
    }

    private fun evictStaleIndexes(settings: QuickOpenSettings.State) {
        val evictAfterMs = settings.luceneEvictionDays * 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val stale = lastAccessed.entries
            .filter { now - it.value > evictAfterMs }
            .map { it.key }
        stale.forEach { root ->
            val manager = managers.remove(root)
            manager?.close()
            lastAccessed.remove(root)
            lastBuiltMs.remove(root)
            try {
                LuceneIndexManager.indexDirForRoot(root).toFile().deleteRecursively()
                LOG.info("Evicted stale Lucene index for $root")
            } catch (_: Exception) {}
        }
    }
}
