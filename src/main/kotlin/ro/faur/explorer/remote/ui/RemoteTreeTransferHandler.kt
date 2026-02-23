package ro.faur.explorer.remote.ui

import ro.faur.explorer.remote.RemoteFileClipboardData
import ro.faur.explorer.remote.RemoteFileTransferable
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.TransferHandler

/**
 * Swing TransferHandler for drag-out from the remote tree.
 *
 * Uses Swing's TransferHandler + dragEnabled=true (NOT IntelliJ's DnDManager DnDSource),
 * as the two DnD systems conflict when both listen to mouse-drag gestures on the same component.
 *
 * Exports [RemoteFileTransferable] carrying the selected SFTP entries wrapped in
 * [RemoteFileClipboardData]. Connection name is obtained from [RemoteBrowserPanel.getConnectionName].
 */
class RemoteTreeTransferHandler(
    private val remoteTree: RemoteBrowserPanel,
) : TransferHandler() {

    override fun getSourceActions(c: JComponent): Int = COPY

    override fun createTransferable(c: JComponent): Transferable? {
        val selected = remoteTree.getSelectedEntries()
        if (selected.isEmpty()) return null
        val connectionName = remoteTree.getConnectionName() ?: return null
        return RemoteFileTransferable(RemoteFileClipboardData(connectionName, selected))
    }
}
