package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.REMOTE_FILE_FLAVOR
import ro.faur.explorer.remote.RemoteFileClipboardData
import ro.faur.explorer.remote.RemoteFileTransferable
import ro.faur.explorer.remote.SftpEntry
import java.awt.datatransfer.DataFlavor

class RemoteFileTransferableTest {

    private val entries = listOf(
        SftpEntry(name = "file1.txt", isDirectory = false, size = 100, path = "/home/user/file1.txt"),
        SftpEntry(name = "dir1", isDirectory = true, size = 0, path = "/home/user/dir1"),
    )
    private val data = RemoteFileClipboardData("testConnection", entries)
    private val transferable = RemoteFileTransferable(data)

    @Test
    fun `supports REMOTE_FILE_FLAVOR`() {
        assertTrue(transferable.isDataFlavorSupported(REMOTE_FILE_FLAVOR))
    }

    @Test
    fun `supports stringFlavor`() {
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
    }

    @Test
    fun `does not support imageFlavor`() {
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
    }

    @Test
    fun `getTransferData for REMOTE_FILE_FLAVOR returns RemoteFileClipboardData`() {
        val result = transferable.getTransferData(REMOTE_FILE_FLAVOR) as RemoteFileClipboardData
        assertEquals("testConnection", result.connectionName)
        assertEquals(2, result.entries.size)
        assertEquals("file1.txt", result.entries[0].name)
    }

    @Test
    fun `getTransferData for stringFlavor returns newline-joined paths`() {
        val result = transferable.getTransferData(DataFlavor.stringFlavor) as String
        assertEquals("/home/user/file1.txt\n/home/user/dir1", result)
    }

    @Test
    fun `transfer data flavors array contains both flavors`() {
        val flavors = transferable.transferDataFlavors
        assertEquals(2, flavors.size)
        assertTrue(flavors.contains(REMOTE_FILE_FLAVOR))
        assertTrue(flavors.contains(DataFlavor.stringFlavor))
    }
}
