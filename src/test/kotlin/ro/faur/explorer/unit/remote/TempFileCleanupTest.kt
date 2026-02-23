package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import ro.faur.explorer.remote.security.SecureTempFileManager
import java.nio.file.Files
import java.nio.file.Path

class TempFileCleanupTest {

    @Test
    fun `cleanupAll removes all tracked files`() {
        val manager = SecureTempFileManager()
        val path1 = manager.createTempFile("host1.example.com", "/var/log/app.log")
        val path2 = manager.createTempFile("host1.example.com", "/etc/config.yml")

        assertTrue(Files.exists(path1))
        assertTrue(Files.exists(path2))

        manager.cleanupAll()

        assertFalse(Files.exists(path1))
        assertFalse(Files.exists(path2))
    }

    @Test
    fun `createTempFile returns same path for same host and remote path`() {
        val manager = SecureTempFileManager()
        val path1 = manager.createTempFile("host1.example.com", "/var/log/app.log")
        val path2 = manager.createTempFile("host1.example.com", "/var/log/app.log")
        assertEquals(path1, path2)
        manager.cleanupAll()
    }

    @Test
    fun `createTempFile returns different paths for different remote paths`() {
        val manager = SecureTempFileManager()
        val path1 = manager.createTempFile("host1.example.com", "/var/log/app.log")
        val path2 = manager.createTempFile("host1.example.com", "/etc/config.yml")
        assertNotEquals(path1, path2)
        manager.cleanupAll()
    }

    @Test
    fun `isTrackedTempFile returns true for tracked files`() {
        val manager = SecureTempFileManager()
        val path = manager.createTempFile("host1.example.com", "/var/log/app.log")
        assertTrue(manager.isTrackedTempFile(path))
        manager.cleanupAll()
    }

    @Test
    fun `isTrackedTempFile returns false after cleanup`() {
        val manager = SecureTempFileManager()
        val path = manager.createTempFile("host1.example.com", "/var/log/app.log")
        manager.cleanupAll()
        assertFalse(manager.isTrackedTempFile(path))
    }

    @Test
    fun `getRemotePath returns correct remote path`() {
        val manager = SecureTempFileManager()
        val path = manager.createTempFile("host1.example.com", "/var/log/app.log")
        assertEquals("/var/log/app.log", manager.getRemotePath(path))
        manager.cleanupAll()
    }

    @Test
    fun `getHost returns correct host`() {
        val manager = SecureTempFileManager()
        val path = manager.createTempFile("host1.example.com", "/var/log/app.log")
        assertEquals("host1.example.com", manager.getHost(path))
        manager.cleanupAll()
    }
}
