package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.DirectoryCache
import ro.faur.explorer.remote.SftpEntry
import java.time.Duration

class DirectoryCacheTest {

    private lateinit var cache: DirectoryCache

    @BeforeEach
    fun setup() {
        cache = DirectoryCache(
            ttl = Duration.ofSeconds(30),
            maxEntries = 5
        )
    }

    @Test
    fun `get returns null for uncached path`() {
        assertNull(cache.get("host:22", "/nonexistent"))
    }

    @Test
    fun `put then get returns cached entries`() {
        val entries = listOf(
            SftpEntry("file1.txt", isDirectory = false, size = 100L),
            SftpEntry("dir1", isDirectory = true, size = 0L),
        )
        cache.put("host:22", "/home/user", entries)
        val result = cache.get("host:22", "/home/user")
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("file1.txt", result[0].name)
    }

    @Test
    fun `get returns null after TTL expires`() {
        val shortCache = DirectoryCache(
            ttl = Duration.ofMillis(50),
            maxEntries = 100
        )
        shortCache.put("host:22", "/path", listOf(SftpEntry("f", false, 0L)))
        Thread.sleep(100)
        assertNull(shortCache.get("host:22", "/path"))
    }

    @Test
    fun `get returns entries before TTL expires`() {
        cache.put("host:22", "/path", listOf(SftpEntry("f", false, 0L)))
        assertNotNull(cache.get("host:22", "/path"))
    }

    @Test
    fun `different hosts have separate caches`() {
        cache.put("hostA:22", "/path", listOf(SftpEntry("a", false, 0L)))
        cache.put("hostB:22", "/path", listOf(SftpEntry("b", false, 0L)))
        assertEquals("a", cache.get("hostA:22", "/path")!![0].name)
        assertEquals("b", cache.get("hostB:22", "/path")!![0].name)
    }

    @Test
    fun `different paths on same host have separate caches`() {
        cache.put("host:22", "/path1", listOf(SftpEntry("a", false, 0L)))
        cache.put("host:22", "/path2", listOf(SftpEntry("b", false, 0L)))
        assertEquals("a", cache.get("host:22", "/path1")!![0].name)
        assertEquals("b", cache.get("host:22", "/path2")!![0].name)
    }

    @Test
    fun `evicts oldest entry when max entries exceeded`() {
        val smallCache = DirectoryCache(ttl = Duration.ofSeconds(30), maxEntries = 3)
        smallCache.put("h:22", "/a", listOf(SftpEntry("a", false, 0L)))
        smallCache.put("h:22", "/b", listOf(SftpEntry("b", false, 0L)))
        smallCache.put("h:22", "/c", listOf(SftpEntry("c", false, 0L)))
        smallCache.put("h:22", "/d", listOf(SftpEntry("d", false, 0L)))
        assertNull(smallCache.get("h:22", "/a"))
        assertNotNull(smallCache.get("h:22", "/b"))
        assertNotNull(smallCache.get("h:22", "/c"))
        assertNotNull(smallCache.get("h:22", "/d"))
    }

    @Test
    fun `accessing entry refreshes its LRU position`() {
        val smallCache = DirectoryCache(ttl = Duration.ofSeconds(30), maxEntries = 3)
        smallCache.put("h:22", "/a", listOf(SftpEntry("a", false, 0L)))
        smallCache.put("h:22", "/b", listOf(SftpEntry("b", false, 0L)))
        smallCache.put("h:22", "/c", listOf(SftpEntry("c", false, 0L)))
        smallCache.get("h:22", "/a")
        smallCache.put("h:22", "/d", listOf(SftpEntry("d", false, 0L)))
        assertNotNull(smallCache.get("h:22", "/a"))
        assertNull(smallCache.get("h:22", "/b"))
    }

    @Test
    fun `invalidate removes specific path`() {
        cache.put("h:22", "/path1", listOf(SftpEntry("a", false, 0L)))
        cache.put("h:22", "/path2", listOf(SftpEntry("b", false, 0L)))
        cache.invalidate("h:22", "/path1")
        assertNull(cache.get("h:22", "/path1"))
        assertNotNull(cache.get("h:22", "/path2"))
    }

    @Test
    fun `invalidateAll clears entire cache for a host`() {
        cache.put("h:22", "/path1", listOf(SftpEntry("a", false, 0L)))
        cache.put("h:22", "/path2", listOf(SftpEntry("b", false, 0L)))
        cache.put("other:22", "/path1", listOf(SftpEntry("c", false, 0L)))
        cache.invalidateAll("h:22")
        assertNull(cache.get("h:22", "/path1"))
        assertNull(cache.get("h:22", "/path2"))
        assertNotNull(cache.get("other:22", "/path1"))
    }

    @Test
    fun `invalidate nonexistent path is no-op`() {
        cache.invalidate("h:22", "/nonexistent")
    }

    @Test
    fun `concurrent puts and gets do not throw`() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    cache.put("h:22", "/thread$threadId/$i", listOf(SftpEntry("f", false, 0L)))
                    cache.get("h:22", "/thread$threadId/$i")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
    }
}
