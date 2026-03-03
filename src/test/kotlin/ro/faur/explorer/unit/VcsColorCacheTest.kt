package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.awt.Color
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Verifies the async cache-on-miss pattern for VCS color lookup.
 *
 * The real FileTreeComponent dispatches ReadAction.nonBlocking on miss;
 * here we test the cache read/write logic in isolation.
 */
class VcsColorCacheTest {

    private val cache = ConcurrentHashMap<String, Optional<Color>>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private var slowCallCount = 0

    /** Mimics getEffectiveVcsColor: returns cached value or null (and records a miss). */
    private fun lookupFromCache(path: String): Color? {
        val cached = cache[path]
        if (cached != null) return cached.orElse(null)
        // Cache miss — in production this dispatches a background task
        if (pending.add(path)) slowCallCount++
        return null
    }

    /** Mimics background task completion: writes to cache, removes from pending. */
    private fun simulateBackgroundComplete(path: String, color: Color?) {
        pending.remove(path)
        cache[path] = Optional.ofNullable(color)
    }

    @Test
    fun `cache miss triggers slow lookup exactly once per path`() {
        assertNull(lookupFromCache("/a/b.kt"))
        assertEquals(1, slowCallCount)

        // Subsequent calls before background completes → still null, no extra dispatch
        assertNull(lookupFromCache("/a/b.kt"))
        assertNull(lookupFromCache("/a/b.kt"))
        assertEquals(1, slowCallCount, "pending set must prevent re-dispatch")
    }

    @Test
    fun `cache hit returns color without dispatching`() {
        simulateBackgroundComplete("/a/b.kt", Color.GREEN)

        val color = lookupFromCache("/a/b.kt")
        assertEquals(Color.GREEN, color)
        assertEquals(0, slowCallCount, "cache hit must not trigger slow path")
    }

    @Test
    fun `cache hit for no-color entry returns null without dispatching`() {
        simulateBackgroundComplete("/a/b.kt", null)

        val color = lookupFromCache("/a/b.kt")
        assertNull(color)
        assertEquals(0, slowCallCount, "null Optional entry must be treated as cached")
    }

    @Test
    fun `cache and pending clear on status change allows re-dispatch`() {
        lookupFromCache("/a/b.kt")
        assertEquals(1, slowCallCount)

        // Status change → clear both
        cache.clear()
        pending.clear()

        lookupFromCache("/a/b.kt")
        assertEquals(2, slowCallCount)
    }
}
