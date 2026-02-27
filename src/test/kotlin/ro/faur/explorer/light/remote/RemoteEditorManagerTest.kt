package ro.faur.explorer.light.remote

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.RemoteEditorManager
import ro.faur.explorer.remote.RemoteEditorManager.Companion.REMOTE_CONNECTION_KEY
import ro.faur.explorer.remote.RemoteEditorManager.Companion.REMOTE_PATH_KEY
import ro.faur.explorer.remote.SftpConnectionManager

class RemoteEditorManagerTest : BasePlatformTestCase() {

    fun `test isRemoteTempFile returns false for normal VirtualFile`() {
        val manager = createManager()
        val file = myFixture.configureByText("test.txt", "hello").virtualFile
        assertFalse(manager.isRemoteTempFile(file))
    }

    fun `test isRemoteTempFile returns true after user data is set`() {
        val manager = createManager()
        val file = myFixture.configureByText("remote.txt", "content").virtualFile
        file.putUserData(REMOTE_PATH_KEY, "/home/user/remote.txt")
        assertTrue(manager.isRemoteTempFile(file))
    }

    fun `test getRemotePath returns correct path from user data`() {
        val manager = createManager()
        val file = myFixture.configureByText("data.yml", "key: value").virtualFile
        file.putUserData(REMOTE_PATH_KEY, "/etc/config/data.yml")
        assertEquals("/etc/config/data.yml", manager.getRemotePath(file))
    }

    fun `test getRemotePath returns null for untagged file`() {
        val manager = createManager()
        val file = myFixture.configureByText("plain.txt", "").virtualFile
        assertNull(manager.getRemotePath(file))
    }

    fun `test getConnectionName returns correct connection name`() {
        val manager = createManager()
        val file = myFixture.configureByText("app.conf", "").virtualFile
        file.putUserData(REMOTE_CONNECTION_KEY, "prod-server")
        assertEquals("prod-server", manager.getConnectionName(file))
    }

    fun `test getConnectionName returns null for untagged file`() {
        val manager = createManager()
        val file = myFixture.configureByText("local.txt", "").virtualFile
        assertNull(manager.getConnectionName(file))
    }


    private fun createManager(): RemoteEditorManager {
        return RemoteEditorManager(
            project = project,
            tempFileManager = ro.faur.explorer.remote.security.SecureTempFileManager(),
        )
    }
}
