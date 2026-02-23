package ro.faur.explorer.remote

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * Custom data flavor for remote SFTP file entries on the clipboard.
 *
 * Used by the cross-panel clipboard copy/paste mechanism: when the user
 * copies files in the remote tree (Ctrl+C), a [RemoteFileTransferable]
 * is placed on IntelliJ's [com.intellij.openapi.ide.CopyPasteManager].
 * The local tree paste handler recognises this flavor and triggers a download.
 */
val REMOTE_FILE_FLAVOR = DataFlavor(
    "application/x-system-explorer-remote-files;class=java.util.List",
    "Remote SFTP Files"
)

/**
 * Transferable payload carrying remote SFTP entries for clipboard operations.
 *
 * Supports two flavors:
 * - [REMOTE_FILE_FLAVOR]: returns a [RemoteFileClipboardData] with connection info and entries.
 * - [DataFlavor.stringFlavor]: returns newline-joined paths (useful for pasting into text editors).
 */
class RemoteFileTransferable(
    val data: RemoteFileClipboardData,
) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(REMOTE_FILE_FLAVOR, DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == REMOTE_FILE_FLAVOR || flavor == DataFlavor.stringFlavor

    override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
        REMOTE_FILE_FLAVOR -> data
        DataFlavor.stringFlavor -> data.entries.joinToString("\n") { it.path }
        else -> throw UnsupportedFlavorException(flavor)
    }
}

/**
 * Data carrier for remote file clipboard operations.
 * Holds the connection name and the list of selected remote entries.
 */
data class RemoteFileClipboardData(
    val connectionName: String,
    val entries: List<SftpEntry>,
)
