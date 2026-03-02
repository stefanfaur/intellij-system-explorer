package ro.faur.explorer.unit

import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.index.IndexRegistry
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import ro.faur.explorer.settings.QuickOpenSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Automated coverage for human verification items 2 and 3:
 * Item 2: "Status bar shows '[Live]' chip in gray for a small root."
 * Item 3: "Chip transitions '[Live]' → '[Indexing...]' → '[Indexed]' for a large root."
 *
 * The three chip states are driven directly by IndexRegistry state:
 *   getManager(root) != null  → "Indexed"
 *   isBuilding(root)          → "Indexing..."
 *   else                      → "Live"
 *
 * IndexRegistry is a Kotlin object singleton. Tests use reflection to inject state
 * without triggering PathManager (IDE-only API), and clean up after each test.
 */
class IndexRegistryStateTest {

    private val testKeys = mutableSetOf<String>()

    @Suppress("UNCHECKED_CAST")
    private fun managers(): ConcurrentHashMap<String, LuceneIndexManager> {
        val f = IndexRegistry::class.java.getDeclaredField("managers")
        f.isAccessible = true
        return f.get(IndexRegistry) as ConcurrentHashMap<String, LuceneIndexManager>
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildingRoots(): MutableSet<String> {
        val f = IndexRegistry::class.java.getDeclaredField("buildingRoots")
        f.isAccessible = true
        return f.get(IndexRegistry) as MutableSet<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun lastAccessed(): ConcurrentHashMap<String, Long> {
        val f = IndexRegistry::class.java.getDeclaredField("lastAccessed")
        f.isAccessible = true
        return f.get(IndexRegistry) as ConcurrentHashMap<String, Long>
    }

    @AfterEach
    fun cleanup() {
        val mgrs = managers()
        val building = buildingRoots()
        val accessed = lastAccessed()
        testKeys.forEach { key ->
            mgrs.remove(key)?.close()
            building.remove(key)
            accessed.remove(key)
        }
        testKeys.clear()
    }

    // ─── Item 2: [Live] chip ──────────────────────────────────────────────────

    @Test
    fun `getManager returns null for unknown root — drives Live chip state`() {
        val root = "/test/registry/live-chip"
        testKeys.add(root)

        assertNull(IndexRegistry.getManager(root),
            "Unknown root has no manager → QuickOpenPanel shows '[Live]' chip")
    }

    @Test
    fun `isBuilding returns false for unknown root`() {
        val root = "/test/registry/not-building"
        testKeys.add(root)

        assertFalse(IndexRegistry.isBuilding(root),
            "Unknown root is not being built")
    }

    // ─── Item 3: [Indexing...] chip ───────────────────────────────────────────

    @Test
    fun `isBuilding returns true when root is in buildingRoots — drives Indexing chip state`() {
        val root = "/test/registry/indexing-chip"
        testKeys.add(root)
        buildingRoots().add(root)

        assertTrue(IndexRegistry.isBuilding(root),
            "Root in buildingRoots → QuickOpenPanel shows '[Indexing...]' chip")
    }

    // ─── Item 3: [Indexed] chip ───────────────────────────────────────────────

    @Test
    fun `getManager returns manager when index is ready — drives Indexed chip state`() {
        val root = "/test/registry/indexed-chip"
        testKeys.add(root)
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)
        managers()[root] = manager

        assertNotNull(IndexRegistry.getManager(root),
            "Root with ready manager → QuickOpenPanel shows '[Indexed]' chip")
        assertSame(manager, IndexRegistry.getManager(root))
    }

    // ─── New APIs: listIndexedRoots, getBuildProgress, registerForTest ────────

    @Test
    fun `listIndexedRoots returns entry after registerForTest`() {
        val root = "/test/root/a"
        testKeys.add(root)
        IndexRegistry.registerForTest(root, LuceneIndexManager(ByteBuffersDirectory()), builtAtMs = 1_000L)
        val roots = IndexRegistry.listIndexedRoots()
        val stats = roots[root]
        assertNotNull(stats, "Expected $root in listIndexedRoots")
        assertEquals(1_000L, stats!!.lastBuiltMs)
    }

    @Test
    fun `getBuildProgress returns 0 when not building`() {
        assertEquals(0, IndexRegistry.getBuildProgress("/nonexistent/root"))
    }

    // ─── Eviction (drives chip returning to Live after eviction) ─────────────

    @Test
    fun `evictStaleIndexes removes roots not accessed within the eviction window`() {
        val staleRoot = "/test/registry/stale-eviction"
        val freshRoot = "/test/registry/fresh-eviction"
        testKeys.add(staleRoot)
        testKeys.add(freshRoot)

        // Stale: last accessed 40 days ago
        val fortyDaysAgoMs = System.currentTimeMillis() - (40L * 24 * 60 * 60 * 1000)
        val staleManager = LuceneIndexManager(ByteBuffersDirectory())
        managers()[staleRoot] = staleManager
        lastAccessed()[staleRoot] = fortyDaysAgoMs

        // Fresh: use as the "current" root so getOrBuild takes the fast path
        // (returns existing manager immediately, still calls evictStaleIndexes as a side effect)
        val freshManager = LuceneIndexManager(ByteBuffersDirectory())
        managers()[freshRoot] = freshManager
        lastAccessed()[freshRoot] = System.currentTimeMillis()

        // Trigger eviction via the fast path of getOrBuild
        val settings = QuickOpenSettings.State(luceneEvictionDays = 30)
        IndexRegistry.getOrBuild(freshRoot, settings) {}

        assertNull(IndexRegistry.getManager(staleRoot),
            "Stale root (last accessed 40 days ago with 30-day window) should be evicted")
        assertNotNull(IndexRegistry.getManager(freshRoot),
            "Fresh root should remain after eviction pass")
    }
}
