package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.SftpFileOperations
import java.nio.file.Files
import java.nio.file.Path

class SftpFileOperationsTest : EmbeddedSshTestBase() {

    private lateinit var ops: SftpFileOperations
    private lateinit var localTempDir: Path

    @BeforeEach
    fun setupOps() {
        ops = SftpFileOperations.create("localhost", serverPort, TEST_USER, TEST_PASSWORD)
        localTempDir = Files.createTempDirectory("sftp-local-test")
    }

    @AfterEach
    fun closeOps() {
        ops.close()
        localTempDir.toFile().deleteRecursively()
    }

    @Test
    fun `listDirectory returns files and folders`() {
        val entries = ops.listDirectory("/")
        val names = entries.map { it.name }
        assertTrue("config" in names)
        assertTrue("logs" in names)
        assertTrue("readme.txt" in names)
    }

    @Test
    fun `listDirectory excludes dot and dotdot`() {
        val entries = ops.listDirectory("/")
        val names = entries.map { it.name }
        assertFalse("." in names)
        assertFalse(".." in names)
    }

    @Test
    fun `listDirectory includes hidden when requested`() {
        val entries = ops.listDirectory("/", includeHidden = true)
        val names = entries.map { it.name }
        assertTrue(".hidden" in names)
    }

    @Test
    fun `listDirectory excludes hidden by default`() {
        val entries = ops.listDirectory("/", includeHidden = false)
        val names = entries.map { it.name }
        assertFalse(".hidden" in names)
    }

    @Test
    fun `listDirectory of nonexistent path throws`() {
        assertThrows(Exception::class.java) {
            ops.listDirectory("/nonexistent/path")
        }
    }

    @Test
    fun `download file creates local copy with correct content`() {
        val localFile = localTempDir.resolve("downloaded.txt")
        ops.download("/readme.txt", localFile)
        assertTrue(Files.exists(localFile))
        assertEquals("Hello World\n", Files.readString(localFile))
    }

    @Test
    fun `download preserves file size`() {
        val localFile = localTempDir.resolve("downloaded.yml")
        ops.download("/config/app.yml", localFile)
        val remoteSize = ops.stat("/config/app.yml").size
        assertEquals(remoteSize, Files.size(localFile))
    }

    @Test
    fun `upload file creates remote copy`() {
        val localFile = localTempDir.resolve("upload.txt")
        Files.writeString(localFile, "uploaded content")
        ops.upload(localFile, "/upload.txt")
        val stat = ops.stat("/upload.txt")
        assertEquals(Files.size(localFile), stat.size)
    }

    @Test
    fun `upload overwrites existing file`() {
        val localFile = localTempDir.resolve("overwrite.txt")
        Files.writeString(localFile, "new content")
        ops.upload(localFile, "/readme.txt")
        val verify = localTempDir.resolve("verify.txt")
        ops.download("/readme.txt", verify)
        assertEquals("new content", Files.readString(verify))
    }

    @Test
    fun `mkdir creates new directory`() {
        ops.mkdir("/newdir")
        val stat = ops.stat("/newdir")
        assertTrue(stat.isDirectory)
    }

    @Test
    fun `mkdir on existing directory throws`() {
        assertThrows(Exception::class.java) {
            ops.mkdir("/config")
        }
    }

    @Test
    fun `rename file changes name on remote`() {
        ops.rename("/readme.txt", "/readme-renamed.txt")
        assertThrows(Exception::class.java) { ops.stat("/readme.txt") }
        assertNotNull(ops.stat("/readme-renamed.txt"))
    }

    @Test
    fun `delete file removes from remote`() {
        ops.deleteFile("/readme.txt")
        assertThrows(Exception::class.java) { ops.stat("/readme.txt") }
    }

    @Test
    fun `delete directory removes from remote`() {
        ops.mkdir("/todelete")
        ops.deleteDirectory("/todelete")
        assertThrows(Exception::class.java) { ops.stat("/todelete") }
    }

    @Test
    fun `stat returns file attributes`() {
        val stat = ops.stat("/readme.txt")
        assertFalse(stat.isDirectory)
        assertTrue(stat.size > 0)
    }

    @Test
    fun `stat returns directory attributes`() {
        val stat = ops.stat("/config")
        assertTrue(stat.isDirectory)
    }

    @Test
    fun `stat of nonexistent path throws`() {
        assertThrows(Exception::class.java) {
            ops.stat("/does/not/exist")
        }
    }
}
