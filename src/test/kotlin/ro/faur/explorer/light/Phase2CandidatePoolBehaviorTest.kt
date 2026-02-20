package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ro.faur.explorer.quickopen.backend.EnumeratorBackend
import ro.faur.explorer.quickopen.index.CandidatePool
import ro.faur.explorer.quickopen.model.CandidateType
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Phase 2 — CandidatePool Behavior
 *
 * Light test (extends BasePlatformTestCase) because CandidatePool.refreshAsync()
 * calls ApplicationManager.invokeLater(), which requires a running IntelliJ application.
 *
 * Behaviors under test:
 *   - An empty enumeration leaves the pool empty
 *   - Pool accumulates candidates from the enumerator
 *   - Pool size does not exceed maxCandidates
 *   - isTruncated is false when results fit within cap
 *   - isTruncated is true when results exceed cap
 *   - refreshAsync replaces the previous pool on second call
 *   - cancel() stops enumeration without throwing
 *   - Candidates produced for directories have type DIRECTORY
 *   - Candidates produced for files have type FILE
 *   - Candidate id has the "fs:" prefix
 *   - Candidate fullPath matches the path string from the enumerator
 *   - Candidate displayName matches the last segment of the path
 *   - Candidate parentPath matches the parent directory string
 *   - MAX_CANDIDATES constant equals 50_000 (scope guardrail from plan)
 *   - Pool is initially empty before any enumeration is started
 */
class Phase2CandidatePoolBehaviorTest : BasePlatformTestCase() {

    private lateinit var pool: CandidatePool
    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        pool = CandidatePool(InfiniteEnumerator(emptyList()), maxCandidates = 10)
        tempDir = File(System.getProperty("java.io.tmpdir"), "cp_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    override fun tearDown() {
        pool.cancel()
        tempDir.deleteRecursively()
        super.tearDown()
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    fun `test pool is empty before enumeration is started`() {
        assertTrue(pool.getCandidates().isEmpty())
    }

    fun `test isTruncated is false before enumeration is started`() {
        assertFalse(pool.isTruncated)
    }

    // -------------------------------------------------------------------------
    // Empty enumeration
    // -------------------------------------------------------------------------

    fun `test empty enumeration leaves pool empty`() {
        val emptyPool = CandidatePool(InfiniteEnumerator(emptyList()), maxCandidates = 10)
        refreshAndWait(emptyPool, "/root")
        assertTrue(emptyPool.getCandidates().isEmpty())
        emptyPool.cancel()
    }

    fun `test empty enumeration does not set isTruncated`() {
        val emptyPool = CandidatePool(InfiniteEnumerator(emptyList()), maxCandidates = 10)
        refreshAndWait(emptyPool, "/root")
        assertFalse(emptyPool.isTruncated)
        emptyPool.cancel()
    }

    // -------------------------------------------------------------------------
    // Normal enumeration within cap
    // -------------------------------------------------------------------------

    fun `test pool accumulates all candidates when under cap`() {
        val paths = listOf("/root/a", "/root/b", "/root/c")
        val testPool = CandidatePool(InfiniteEnumerator(paths), maxCandidates = 10)
        refreshAndWait(testPool, "/root")
        assertEquals(3, testPool.getCandidates().size)
        testPool.cancel()
    }

    fun `test isTruncated is false when candidates fit within cap`() {
        val paths = listOf("/root/a", "/root/b")
        val testPool = CandidatePool(InfiniteEnumerator(paths), maxCandidates = 10)
        refreshAndWait(testPool, "/root")
        assertFalse(testPool.isTruncated)
        testPool.cancel()
    }

    // -------------------------------------------------------------------------
    // Cap enforcement and truncation detection
    // -------------------------------------------------------------------------

    fun `test pool size does not exceed maxCandidates`() {
        val paths = (1..20).map { "/root/file$it" }
        val testPool = CandidatePool(InfiniteEnumerator(paths), maxCandidates = 5)
        refreshAndWait(testPool, "/root")
        assertTrue("Expected at most 5 candidates, got ${testPool.getCandidates().size}",
            testPool.getCandidates().size <= 5)
        testPool.cancel()
    }

    fun `test isTruncated is true when enumerator produces more than maxCandidates`() {
        val paths = (1..20).map { "/root/file$it" }
        val testPool = CandidatePool(InfiniteEnumerator(paths), maxCandidates = 5)
        refreshAndWait(testPool, "/root")
        assertTrue("isTruncated should be true when enumerator exceeded the cap",
            testPool.isTruncated)
        testPool.cancel()
    }

    // -------------------------------------------------------------------------
    // Refresh replaces previous pool
    // -------------------------------------------------------------------------

    fun `test second refreshAsync replaces the first pool contents`() {
        val firstPaths = listOf("/root/alpha", "/root/beta")
        val secondPaths = listOf("/root/gamma")

        val testPool = CandidatePool(SwitchableEnumerator(firstPaths, secondPaths), maxCandidates = 10)
        refreshAndWait(testPool, "/root")
        val afterFirst = testPool.getCandidates().map { it.fullPath }

        refreshAndWait(testPool, "/other")
        val afterSecond = testPool.getCandidates().map { it.fullPath }

        assertFalse("Second refresh should replace previous pool, but old paths are still present",
            afterSecond.containsAll(afterFirst))
        testPool.cancel()
    }

    fun `test isTruncated is reset to false at start of refreshAsync`() {
        val manyPaths = (1..20).map { "/root/file$it" }
        val fewPaths = listOf("/root/single")

        val testPool = CandidatePool(SwitchableEnumerator(manyPaths, fewPaths), maxCandidates = 5)

        refreshAndWait(testPool, "/root")    // exceeds cap → isTruncated = true
        assertTrue(testPool.isTruncated)

        refreshAndWait(testPool, "/other")   // only 1 result → should clear isTruncated
        assertFalse("isTruncated should be reset when second enumeration fits within cap",
            testPool.isTruncated)
        testPool.cancel()
    }

    // -------------------------------------------------------------------------
    // Candidate field correctness (using real temp dir)
    // -------------------------------------------------------------------------

    fun `test candidate id has fs colon prefix`() {
        val file = File(tempDir, "sample.txt").also { it.writeText("x") }
        val testPool = CandidatePool(SinglePathEnumerator(file.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertTrue("Candidate id should start with 'fs:' but was '${candidate!!.id}'",
            candidate.id.startsWith("fs:"))
        testPool.cancel()
    }

    fun `test candidate fullPath matches path from enumerator`() {
        val file = File(tempDir, "myfile.txt").also { it.writeText("x") }
        val testPool = CandidatePool(SinglePathEnumerator(file.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertEquals(file.absolutePath, candidate!!.fullPath)
        testPool.cancel()
    }

    fun `test candidate displayName is the last segment of the path`() {
        val file = File(tempDir, "last_segment.txt").also { it.writeText("x") }
        val testPool = CandidatePool(SinglePathEnumerator(file.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertEquals("last_segment.txt", candidate!!.displayName)
        testPool.cancel()
    }

    fun `test candidate parentPath is the parent directory of the path`() {
        val file = File(tempDir, "child.txt").also { it.writeText("x") }
        val testPool = CandidatePool(SinglePathEnumerator(file.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertEquals(tempDir.absolutePath, candidate!!.parentPath)
        testPool.cancel()
    }

    fun `test file candidate has type FILE`() {
        val file = File(tempDir, "regular.txt").also { it.writeText("x") }
        val testPool = CandidatePool(SinglePathEnumerator(file.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertEquals(CandidateType.FILE, candidate!!.type)
        testPool.cancel()
    }

    fun `test directory candidate has type DIRECTORY`() {
        val dir = File(tempDir, "subdir").also { it.mkdirs() }
        val testPool = CandidatePool(SinglePathEnumerator(dir.absolutePath), maxCandidates = 10)
        refreshAndWait(testPool, tempDir.absolutePath)
        val candidate = testPool.getCandidates().firstOrNull()
        assertNotNull(candidate)
        assertEquals(CandidateType.DIRECTORY, candidate!!.type)
        testPool.cancel()
    }

    // -------------------------------------------------------------------------
    // MAX_CANDIDATES constant
    // -------------------------------------------------------------------------

    fun `test MAX_CANDIDATES constant is 50000`() {
        assertEquals(50_000, CandidatePool.MAX_CANDIDATES)
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    fun `test cancel does not throw`() {
        var threw = false
        try { pool.cancel() } catch (e: Exception) { threw = true }
        assertFalse("cancel() should not throw", threw)
    }

    fun `test cancel before refreshAsync does not throw`() {
        val testPool = CandidatePool(InfiniteEnumerator(emptyList()), maxCandidates = 10)
        var threw = false
        try { testPool.cancel() } catch (e: Exception) { threw = true }
        assertFalse("cancel() before any refresh should not throw", threw)
    }

    // -------------------------------------------------------------------------
    // Helpers / fake backends
    // -------------------------------------------------------------------------

    /**
     * Calls [CandidatePool.refreshAsync] with [root] and blocks (up to 5 s) until
     * the onUpdate callback fires via invokeLater.  Replaces the old fixed-sleep
     * waitForPool() which blocked the test thread for 2 s unconditionally.
     */
    private fun refreshAndWait(p: CandidatePool, root: String) {
        val latch = CountDownLatch(1)
        p.refreshAsync(root) { latch.countDown() }
        assertTrue("CandidatePool should finish within 5s for root=$root",
            latch.await(5, TimeUnit.SECONDS))
    }

    class InfiniteEnumerator(private val paths: List<String>) : EnumeratorBackend {
        override val name = "InfiniteEnumerator"
        override fun isAvailable() = true
        override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
            for (path in paths) emit(path)
        }
    }

    class SwitchableEnumerator(
        private val firstPaths: List<String>,
        private val secondPaths: List<String>
    ) : EnumeratorBackend {
        private var callCount = 0
        override val name = "SwitchableEnumerator"
        override fun isAvailable() = true
        override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
            val paths = if (callCount++ == 0) firstPaths else secondPaths
            for (path in paths) emit(path)
        }
    }

    class SinglePathEnumerator(private val path: String) : EnumeratorBackend {
        override val name = "SinglePathEnumerator"
        override fun isAvailable() = true
        override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
            emit(path)
        }
    }
}
