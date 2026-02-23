package ro.faur.explorer.remote

import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class DirectoryCache(
    private val ttl: Duration,
    private val maxEntries: Int,
) {
    private data class CacheEntry(
        val entries: List<SftpEntry>,
        val insertedAt: Instant,
    )

    // Access-order LinkedHashMap wrapped for thread safety via a ReentrantReadWriteLock.
    // We use a single flat map keyed by "$hostKey:$path" and implement LRU eviction manually.
    private val lock = ReentrantReadWriteLock()
    private val lruMap: LinkedHashMap<String, CacheEntry> = object : LinkedHashMap<String, CacheEntry>(
        16, 0.75f, true /* access-order */
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean {
            return size > maxEntries
        }
    }

    private fun cacheKey(hostKey: String, path: String): String = "$hostKey:$path"

    fun get(hostKey: String, path: String): List<SftpEntry>? {
        val key = cacheKey(hostKey, path)
        // We need a write lock here because access-order LinkedHashMap mutates on get().
        lock.write {
            val entry = lruMap[key] ?: return null
            if (Duration.between(entry.insertedAt, Instant.now()) > ttl) {
                lruMap.remove(key)
                return null
            }
            return entry.entries
        }
    }

    fun put(hostKey: String, path: String, entries: List<SftpEntry>) {
        val key = cacheKey(hostKey, path)
        lock.write {
            lruMap[key] = CacheEntry(entries, Instant.now())
        }
    }

    fun invalidate(hostKey: String, path: String) {
        val key = cacheKey(hostKey, path)
        lock.write {
            lruMap.remove(key)
        }
    }

    fun invalidateAll(hostKey: String) {
        val prefix = "$hostKey:"
        lock.write {
            val keysToRemove = lruMap.keys.filter { it.startsWith(prefix) }
            keysToRemove.forEach { lruMap.remove(it) }
        }
    }
}
