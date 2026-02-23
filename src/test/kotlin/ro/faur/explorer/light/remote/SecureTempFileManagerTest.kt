package ro.faur.explorer.light.remote

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.security.SecureTempFileManager
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class SecureTempFileManagerTest : BasePlatformTestCase() {

    private lateinit var manager: SecureTempFileManager

    override fun setUp() {
        super.setUp()
        manager = SecureTempFileManager()
    }

    override fun tearDown() {
        try {
            manager.cleanupAll()
        } finally {
            super.tearDown()
        }
    }

    fun `test createTempFile returns path in secure directory`() {
        val path = manager.createTempFile("prod.example.com", "/var/www/app.yml")
        assertTrue(Files.exists(path))
        assertTrue(path.toString().contains("prod.example.com"))
        assertEquals("app.yml", path.fileName.toString())
    }

    fun `test temp file has restricted permissions on Unix`() {
        val path = manager.createTempFile("host", "/file.txt")
        if (System.getProperty("os.name").lowercase().contains("linux") ||
            System.getProperty("os.name").lowercase().contains("mac")) {
            val perms = Files.getPosixFilePermissions(path)
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ))
            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE))
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
        }
    }

    fun `test temp directory has restricted permissions`() {
        val path = manager.createTempFile("host", "/dir/file.txt")
        val dir = path.parent
        if (System.getProperty("os.name").lowercase().let { "linux" in it || "mac" in it }) {
            val perms = Files.getPosixFilePermissions(dir)
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ))
            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE))
            assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE))
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
        }
    }

    fun `test same remote path returns same local path`() {
        val path1 = manager.createTempFile("host", "/var/app.yml")
        val path2 = manager.createTempFile("host", "/var/app.yml")
        assertEquals(path1, path2)
    }

    fun `test different remote paths return different local paths`() {
        val path1 = manager.createTempFile("host", "/var/a.yml")
        val path2 = manager.createTempFile("host", "/var/b.yml")
        assertFalse(path1 == path2)
    }

    fun `test cleanupAll removes all temp files`() {
        val path1 = manager.createTempFile("host", "/a.txt")
        val path2 = manager.createTempFile("host", "/b.txt")
        Files.writeString(path1, "content a")
        Files.writeString(path2, "content b")
        manager.cleanupAll()
        assertFalse(Files.exists(path1))
        assertFalse(Files.exists(path2))
    }

    fun `test getRemotePath returns original remote path`() {
        val path = manager.createTempFile("host", "/var/config/app.yml")
        val remotePath = manager.getRemotePath(path)
        assertEquals("/var/config/app.yml", remotePath)
    }

    fun `test getHost returns original host`() {
        val path = manager.createTempFile("prod.example.com", "/file.txt")
        val host = manager.getHost(path)
        assertEquals("prod.example.com", host)
    }

    fun `test isTrackedTempFile returns true for managed files`() {
        val path = manager.createTempFile("host", "/file.txt")
        assertTrue(manager.isTrackedTempFile(path))
    }

    fun `test isTrackedTempFile returns false for unmanaged files`() {
        val randomFile = Files.createTempFile("random", ".txt")
        assertFalse(manager.isTrackedTempFile(randomFile))
    }
}
