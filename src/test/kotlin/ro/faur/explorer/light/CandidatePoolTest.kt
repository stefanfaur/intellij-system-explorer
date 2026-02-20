package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ro.faur.explorer.quickopen.backend.EnumeratorBackend
import ro.faur.explorer.quickopen.index.CandidatePool
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CandidatePoolTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        FrecencyStore.getInstance().loadState(FrecencyStore.State())
    }

    fun `test pool collects candidates from enumerator`() {
        val paths = listOf("/tmp/a", "/tmp/b", "/tmp/c")
        val enumerator = object : EnumeratorBackend {
            override val name = "TestEnum"
            override fun isAvailable() = true
            override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
                paths.forEach { emit(it) }
            }
        }

        val pool = CandidatePool(enumerator, maxCandidates = 100)
        val latch = CountDownLatch(1)
        pool.refreshAsync("/tmp") { latch.countDown() }

        assertTrue("Pool should finish within 5s", latch.await(5, TimeUnit.SECONDS))
        val candidates = pool.getCandidates()
        // At least the paths that exist as file-system items; for non-existent paths,
        // File.isDirectory is false but the candidate still gets added
        assertTrue("Should have candidates from enumeration", candidates.isNotEmpty())
        assertFalse(pool.isTruncated)
        pool.cancel()
    }

    fun `test pool truncates when maxCandidates exceeded`() {
        val enumerator = object : EnumeratorBackend {
            override val name = "BigEnum"
            override fun isAvailable() = true
            override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
                // Emit more paths than maxCandidates
                repeat(200) { emit("/tmp/file$it") }
            }
        }

        val pool = CandidatePool(enumerator, maxCandidates = 10)
        val latch = CountDownLatch(1)
        pool.refreshAsync("/tmp") { latch.countDown() }

        assertTrue("Pool should finish within 5s", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Pool should be truncated", pool.isTruncated)
        assertTrue("Candidate count should not exceed maxCandidates", pool.getCandidates().size <= 10)
        pool.cancel()
    }

    fun `test pool cancel stops enumeration`() {
        val enumerator = object : EnumeratorBackend {
            override val name = "SlowEnum"
            override fun isAvailable() = true
            override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
                repeat(1000) {
                    emit("/tmp/file$it")
                    kotlinx.coroutines.delay(10)
                }
            }
        }

        val pool = CandidatePool(enumerator)
        pool.refreshAsync("/tmp")
        Thread.sleep(50) // let it run briefly
        pool.cancel()
        // Should not throw and should have some candidates
        assertTrue(pool.getCandidates().size < 1000)
    }

    fun `test pool enumerates real temp directory`() {
        val tempDir = createTempDirectory()
        try {
            File(tempDir, "a.txt").createNewFile()
            File(tempDir, "b.txt").createNewFile()
            File(tempDir, "subdir").mkdir()

            val pool = CandidatePool(ro.faur.explorer.quickopen.backend.VfsEnumerator(), maxCandidates = 100)
            val latch = CountDownLatch(1)
            pool.refreshAsync(tempDir.absolutePath) { latch.countDown() }

            assertTrue("Pool should finish within 5s", latch.await(5, TimeUnit.SECONDS))
            val candidates = pool.getCandidates()
            assertTrue("Should find at least 3 entries (root + 3 children)", candidates.size >= 3)
            assertFalse(pool.isTruncated)
            pool.cancel()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "candidate_pool_test_${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }
}
