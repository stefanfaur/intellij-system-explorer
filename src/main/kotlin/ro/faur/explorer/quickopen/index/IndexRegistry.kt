package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ro.faur.explorer.settings.QuickOpenSettings
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object IndexRegistry {
    private val managers = ConcurrentHashMap<String, LuceneIndexManager>()
    private val lastAccessed = ConcurrentHashMap<String, Long>()
    private val buildingRoots = ConcurrentHashMap.newKeySet<String>()  // roots currently being built
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

        registryScope.launch(Dispatchers.IO) {
            try {
                val rootPath = Paths.get(root)
                val indexPath = LuceneIndexManager.indexDirForRoot(root)
                Files.createDirectories(indexPath)
                val manager = LuceneIndexManager(indexPath)
                LuceneIndexBuilder.buildIndex(rootPath, manager, settings)
                managers[root] = manager
                buildingRoots.remove(root)
                onIndexReady()  // callback so QuickOpenPanel can switch to LuceneEnumerator
                // Start the watcher after build completes
                LuceneIndexBuilder.startWatcher(rootPath, manager, settings, registryScope)
            } catch (e: Exception) {
                LOG.warn("Failed to build Lucene index for $root", e)
                buildingRoots.remove(root)
            }
        }
        return null  // index not ready yet; caller falls back to live ripgrep
    }

    fun isBuilding(root: String): Boolean = root in buildingRoots

    fun getManager(root: String): LuceneIndexManager? = managers[root]

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
            try {
                LuceneIndexManager.indexDirForRoot(root).toFile().deleteRecursively()
                LOG.info("Evicted stale Lucene index for $root")
            } catch (_: Exception) {}
        }
    }
}
