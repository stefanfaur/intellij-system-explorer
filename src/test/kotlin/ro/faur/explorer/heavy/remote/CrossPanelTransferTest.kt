package ro.faur.explorer.heavy.remote

import com.intellij.testFramework.HeavyPlatformTestCase
import ro.faur.explorer.remote.REMOTE_FILE_FLAVOR
import ro.faur.explorer.remote.RemoteFileClipboardData
import ro.faur.explorer.remote.RemoteFileTransferable
import ro.faur.explorer.remote.SftpEntry
import java.awt.datatransfer.DataFlavor

class CrossPanelTransferTest : HeavyPlatformTestCase() {

    fun `test RemoteFileTransferable supports custom and string flavors`() {
        val entries = listOf(
            SftpEntry("app.yml", isDirectory = false, size = 100L),
        )
        val transferable = RemoteFileTransferable(RemoteFileClipboardData("prod-server", entries))

        assertTrue(transferable.isDataFlavorSupported(REMOTE_FILE_FLAVOR))
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
    }

    fun `test RemoteFileTransferable string flavor returns paths`() {
        val entries = listOf(
            SftpEntry("/var/www/a.txt", isDirectory = false, size = 10L),
            SftpEntry("/var/www/b.txt", isDirectory = false, size = 20L),
        )
        val transferable = RemoteFileTransferable(RemoteFileClipboardData("prod", entries))
        val text = transferable.getTransferData(DataFlavor.stringFlavor) as String

        assertTrue(text.contains("/var/www/a.txt"))
        assertTrue(text.contains("/var/www/b.txt"))
    }

    fun `test RemoteFileTransferable custom flavor returns RemoteFileClipboardData`() {
        val entries = listOf(
            SftpEntry("/etc/config.yml", isDirectory = false, size = 50L),
        )
        val transferable = RemoteFileTransferable(RemoteFileClipboardData("staging", entries))
        val data = transferable.getTransferData(REMOTE_FILE_FLAVOR) as RemoteFileClipboardData

        assertEquals("staging", data.connectionName)
        assertEquals(1, data.entries.size)
        assertEquals("/etc/config.yml", data.entries[0].path)
    }
}
