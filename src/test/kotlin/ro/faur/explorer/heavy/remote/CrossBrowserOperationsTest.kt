package ro.faur.explorer.heavy.remote

import com.intellij.testFramework.HeavyPlatformTestCase
import ro.faur.explorer.remote.CrossPanelTransferService
import ro.faur.explorer.remote.CrossPanelTransferService.BatchTransferException
import ro.faur.explorer.remote.CrossPanelTransferService.TransferError
import ro.faur.explorer.remote.DirectoryCache
import java.time.Duration

class CrossBrowserOperationsTest : HeavyPlatformTestCase() {

    fun `test TransferError data class stores source dest and cause`() {
        val cause = RuntimeException("connection reset")
        val error = TransferError("/local/file.txt", "/remote/file.txt", cause)

        assertEquals("/local/file.txt", error.sourcePath)
        assertEquals("/remote/file.txt", error.destinationPath)
        assertSame(cause, error.cause)
    }

    fun `test BatchTransferException collects per-file errors`() {
        val errors = listOf(
            TransferError("/a.txt", "/remote/a.txt", RuntimeException("err1")),
            TransferError("/b.txt", "/remote/b.txt", RuntimeException("err2")),
        )
        val ex = BatchTransferException("Upload completed with 2 error(s)", errors)

        assertEquals(2, ex.errors.size)
        assertTrue(ex.message!!.contains("2 error(s)"))
        assertEquals("/a.txt", ex.errors[0].sourcePath)
        assertEquals("/b.txt", ex.errors[1].sourcePath)
    }

    fun `test DirectoryCache is invalidated after put`() {
        val cache = DirectoryCache(ttl = Duration.ofMinutes(5), maxEntries = 100)
        val entries = listOf(
            ro.faur.explorer.remote.SftpEntry("file.txt", isDirectory = false, size = 10L)
        )
        cache.put("conn", "/home", entries)
        assertNotNull(cache.get("conn", "/home"))

        cache.invalidate("conn", "/home")
        assertNull(cache.get("conn", "/home"))
    }

    fun `test DirectoryCache invalidateAll clears all paths for connection`() {
        val cache = DirectoryCache(ttl = Duration.ofMinutes(5), maxEntries = 100)
        val entry = listOf(
            ro.faur.explorer.remote.SftpEntry("x", isDirectory = false, size = 1L)
        )
        cache.put("conn", "/a", entry)
        cache.put("conn", "/b", entry)
        cache.put("other", "/a", entry)

        cache.invalidateAll("conn")
        assertNull(cache.get("conn", "/a"))
        assertNull(cache.get("conn", "/b"))
        assertNotNull(cache.get("other", "/a"))
    }
}
