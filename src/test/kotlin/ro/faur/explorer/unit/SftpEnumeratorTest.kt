package ro.faur.explorer.unit

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.sshd.sftp.client.SftpClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import ro.faur.explorer.quickopen.backend.SftpEnumerator
import ro.faur.explorer.remote.SftpConnectionManager

/**
 * Unit tests for SftpEnumerator
 *
 * Tests the SFTP-based file enumerator that enables Quick Open to work over
 * remote SSH connections.
 */
class SftpEnumeratorTest {

    private lateinit var mockSftpClient: SftpClient
    private lateinit var mockConnectionManager: SftpConnectionManager

    @BeforeEach
    fun setUp() {
        mockSftpClient = mock(SftpClient::class.java)
        mockConnectionManager = mock(SftpConnectionManager::class.java)
    }

    @Test
    fun `name returns SftpEnumerator`() {
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertEquals("SftpEnumerator", enumerator.name)
    }

    @Test
    fun `isAvailable returns false when not connected`() {
        `when`(mockConnectionManager.isConnected("test-conn")).thenReturn(false)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertFalse(enumerator.isAvailable())
    }

    @Test
    fun `isAvailable returns true when connected`() {
        `when`(mockConnectionManager.isConnected("test-conn")).thenReturn(true)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertTrue(enumerator.isAvailable())
    }

    @Test
    fun `enumerate returns empty when no SFTP client available`() = runBlocking {
        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(null)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")
        val paths = enumerator.enumerate("/home/user", 100).toList()
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `enumerate returns files from SFTP directory listing`() = runBlocking {
        // Create mock DirEntry for a file
        val mockAttrs = mock(SftpClient.Attributes::class.java)
        val mockEntry = mock(SftpClient.DirEntry::class.java)
        `when`(mockEntry.filename).thenReturn("testfile.txt")
        `when`(mockEntry.attributes).thenReturn(mockAttrs)
        `when`(mockAttrs.isDirectory).thenReturn(false)

        `when`(mockSftpClient.readDir(any(String::class.java))).thenReturn(listOf(mockEntry))
        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)

        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")
        val paths = enumerator.enumerate("/home/user", 100).toList()

        assertTrue(paths.any { it.endsWith("testfile.txt") }, "Should contain file entry")
    }

    @Test
    fun `enumerate respects maxResults limit`() = runBlocking {
        // Create many mock file entries
        val fileEntries = (1..10).map { i ->
            val mockAttrs = mock(SftpClient.Attributes::class.java)
            val mockEntry = mock(SftpClient.DirEntry::class.java)
            `when`(mockEntry.filename).thenReturn("file$i.txt")
            `when`(mockEntry.attributes).thenReturn(mockAttrs)
            `when`(mockAttrs.isDirectory).thenReturn(false)
            mockEntry
        }
        `when`(mockSftpClient.readDir(any(String::class.java))).thenReturn(fileEntries)

        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")

        val paths = enumerator.enumerate("/home/user", 5).toList()

        assertEquals(5, paths.size, "Should respect maxResults limit")
    }

    @Test
    fun `enumerate skips ignored directories`() = runBlocking {
        // Create entries including ignored directories
        val normalFile = createMockEntry("normal.txt", isDirectory = false)
        val ignoredDir = createMockEntry("node_modules", isDirectory = true)
        val normalDir = createMockEntry("src", isDirectory = true)
        val subDirFile = createMockEntry("code.kt", isDirectory = false)

        `when`(mockSftpClient.readDir("/home/user"))
            .thenReturn(listOf(normalFile, ignoredDir, normalDir))
        `when`(mockSftpClient.readDir("/home/user/src"))
            .thenReturn(listOf(subDirFile))
        // node_modules should NOT be read

        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")

        val paths = enumerator.enumerate("/home/user", 100).toList()

        assertTrue(paths.any { it.endsWith("normal.txt") }, "Should contain normal file")
        assertTrue(paths.any { it.endsWith("code.kt") }, "Should descend into normal directory")
    }

    @Test
    fun `enumerate handles SFTP errors gracefully`() = runBlocking {
        `when`(mockSftpClient.readDir(any(String::class.java)))
            .thenThrow(RuntimeException("Permission denied"))

        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")

        // Should not throw - errors should be logged and skipped
        val paths = enumerator.enumerate("/home/user", 100).toList()
        assertTrue(paths.isEmpty(), "Should return empty on error")
    }

    @Test
    fun `enumerate handles root path correctly`() = runBlocking {
        val rootFile = createMockEntry("readme.txt", isDirectory = false)
        `when`(mockSftpClient.readDir("/")).thenReturn(listOf(rootFile))

        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/")

        val paths = enumerator.enumerate("/", 100).toList()

        assertTrue(paths.any { it == "/readme.txt" }, "Root files should have leading slash")
    }

    @Test
    fun `enumerate skips dot entries`() = runBlocking {
        val dotEntry = createMockEntry(".", isDirectory = true)
        val dotDotEntry = createMockEntry("..", isDirectory = true)
        val normalFile = createMockEntry("file.txt", isDirectory = false)

        `when`(mockSftpClient.readDir("/home/user"))
            .thenReturn(listOf(dotEntry, dotDotEntry, normalFile))

        `when`(mockConnectionManager.getSftpClient("test-conn")).thenReturn(mockSftpClient)
        val enumerator = SftpEnumerator(mockConnectionManager, "test-conn", "/home/user")

        val paths = enumerator.enumerate("/home/user", 100).toList()

        assertEquals(1, paths.size, "Should only contain normal file")
        assertFalse(paths.any { it.endsWith(".") || it.endsWith("..") }, "Should not contain dot entries")
    }

    private fun createMockEntry(filename: String, isDirectory: Boolean): SftpClient.DirEntry {
        val mockAttrs = mock(SftpClient.Attributes::class.java)
        val mockEntry = mock(SftpClient.DirEntry::class.java)
        `when`(mockEntry.filename).thenReturn(filename)
        `when`(mockEntry.attributes).thenReturn(mockAttrs)
        `when`(mockAttrs.isDirectory).thenReturn(isDirectory)
        return mockEntry
    }
}
