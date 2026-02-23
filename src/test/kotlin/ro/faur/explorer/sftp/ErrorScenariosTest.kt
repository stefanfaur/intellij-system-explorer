package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.SftpFileOperations
import java.nio.file.Files

class ErrorScenariosTest : EmbeddedSshTestBase() {

    @Test
    fun `permission denied on restricted directory`() {
        val restricted = serverRoot.resolve("restricted")
        Files.createDirectories(restricted)
        restricted.toFile().setReadable(false)
        createOps().use { ops ->
            assertThrows(Exception::class.java) {
                ops.listDirectory("/restricted")
            }
        }
    }

    @Test
    fun `download nonexistent file throws`() {
        createOps().use { ops ->
            val localFile = Files.createTempDirectory("test").resolve("output.txt")
            assertThrows(Exception::class.java) {
                ops.download("/does/not/exist.txt", localFile)
            }
        }
    }

    @Test
    fun `upload to nonexistent parent directory throws`() {
        createOps().use { ops ->
            val localFile = Files.createTempFile("test", ".txt")
            Files.writeString(localFile, "content")
            assertThrows(Exception::class.java) {
                ops.upload(localFile, "/nonexistent/parent/file.txt")
            }
        }
    }

    @Test
    fun `rename nonexistent file throws`() {
        createOps().use { ops ->
            assertThrows(Exception::class.java) {
                ops.rename("/ghost.txt", "/renamed.txt")
            }
        }
    }

    @Test
    fun `delete nonexistent file throws`() {
        createOps().use { ops ->
            assertThrows(Exception::class.java) {
                ops.deleteFile("/ghost.txt")
            }
        }
    }

    @Test
    fun `operations fail after disconnect`() {
        val ops = createOps()
        ops.close()
        assertThrows(Exception::class.java) {
            ops.listDirectory("/")
        }
    }

    @Test
    fun `auth failure with wrong username`() {
        assertThrows(Exception::class.java) {
            SftpFileOperations.create("localhost", serverPort, "wronguser", TEST_PASSWORD)
        }
    }

    @Test
    fun `connection to wrong port fails`() {
        assertThrows(Exception::class.java) {
            SftpFileOperations.create("localhost", serverPort + 9999, TEST_USER, TEST_PASSWORD)
        }
    }

    private fun createOps(): SftpFileOperations {
        return SftpFileOperations.create("localhost", serverPort, TEST_USER, TEST_PASSWORD)
    }
}
