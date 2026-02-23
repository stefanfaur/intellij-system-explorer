package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import ro.faur.explorer.remote.SftpFileOperations
import java.nio.file.Files

class SymlinkHandlingTest : EmbeddedSshTestBase() {

    private lateinit var ops: SftpFileOperations

    @BeforeEach
    fun setupOps() {
        ops = SftpFileOperations.create("localhost", serverPort, TEST_USER, TEST_PASSWORD)
        val broken = serverRoot.resolve("broken-link")
        Files.createSymbolicLink(broken, serverRoot.resolve("nonexistent-target"))
    }

    @AfterEach
    fun closeOps() {
        ops.close()
    }

    @Test
    fun `listDirectory identifies symlinks`() {
        val entries = ops.listDirectory("/", includeHidden = true)
        val link = entries.find { it.name == "config-link" }
        assertNotNull(link)
        assertTrue(link!!.isSymlink)
    }

    @Test
    fun `resolveLink returns symlink target`() {
        val target = ops.resolveLink("/config-link")
        assertNotNull(target)
        assertTrue(target!!.endsWith("config"))
    }

    @Test
    fun `broken symlink is detected`() {
        val entries = ops.listDirectory("/", includeHidden = true)
        val broken = entries.find { it.name == "broken-link" }
        assertNotNull(broken)
        assertTrue(broken!!.isSymlink)
        assertTrue(broken.isBrokenSymlink)
    }

    @Test
    fun `navigating into symlinked directory works`() {
        val entries = ops.listDirectory("/config-link")
        val names = entries.map { it.name }
        assertTrue("app.yml" in names)
    }
}
