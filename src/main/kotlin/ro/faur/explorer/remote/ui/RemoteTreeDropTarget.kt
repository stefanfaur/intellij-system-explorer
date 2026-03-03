package ro.faur.explorer.remote.ui

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ro.faur.explorer.remote.CrossPanelTransferService
import ro.faur.explorer.util.explorerExceptionHandler
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * IntelliJ DnDNativeTarget for receiving drops on the remote tree.
 *
 * Handles uploads when files are dropped from:
 * - IntelliJ's Project View (TransferableWrapper / FileFlavorProvider)
 * - External applications via native AWT DnD (javaFileListFlavor)
 *
 * IMPORTANT: TransferableWrapper extends FileFlavorProvider, NOT Transferable.
 * Use wrapper.asFileList() directly — do NOT iterate Transferable flavors.
 */
class RemoteTreeDropTarget(
    private val remoteTree: RemoteBrowserPanel,
    private val transferService: CrossPanelTransferService?,
    private val connectionName: String,
) : DnDNativeTarget {

    private var dropScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun reconnect() {
        dropScope.cancel()
        dropScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun dispose() {
        dropScope.cancel()
    }

    override fun update(event: DnDEvent): Boolean {
        val canDrop = transferService != null && hasFileFlavor(event)
        event.isDropPossible = canDrop
        return canDrop
    }

    override fun drop(event: DnDEvent) {
        val service = transferService ?: return

        val targetDir = resolveTargetDirectory()
        val localFiles = extractLocalFiles(event)

        if (localFiles.isEmpty()) return

        // Convert java.io.File list to VirtualFile list for the transfer service
        val virtualFiles = localFiles.mapNotNull { file ->
            com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(file)
        }

        if (virtualFiles.isEmpty()) return

        dropScope.launch(explorerExceptionHandler(null, "Remote file drop")) {
            try {
                service.uploadAsync(virtualFiles, targetDir, connectionName)
                ApplicationManager.getApplication().invokeLater {
                    remoteTree.refresh()
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    javax.swing.JOptionPane.showMessageDialog(
                        remoteTree,
                        "Upload failed: ${e.message}",
                        "Upload Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Determines if the DnD event carries any file-like payload we can handle.
     */
    private fun hasFileFlavor(event: DnDEvent): Boolean {
        // IntelliJ Project View drops (TransferableWrapper / FileFlavorProvider)
        if (event.attachedObject is TransferableWrapper) return true

        // Native AWT drops with javaFileListFlavor.
        // DnDEvent itself extends java.awt.datatransfer.Transferable — use event directly.
        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true

        return false
    }

    /**
     * Extracts a list of local [File] objects from the DnD event.
     *
     * Handles both IntelliJ TransferableWrapper (Project View) and native AWT drops.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractLocalFiles(event: DnDEvent): List<File> {
        val attached = event.attachedObject

        // IntelliJ Project View: TransferableWrapper extends FileFlavorProvider, not Transferable.
        // Use asFileList() directly — flavor iteration will NOT work.
        if (attached is TransferableWrapper) {
            return attached.asFileList()?.filterNotNull() ?: emptyList()
        }

        // Native AWT drop (external apps, Finder, Explorer, etc.)
        // DnDEvent itself extends java.awt.datatransfer.Transferable — use event directly.
        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return runCatching {
                (event.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>() ?: emptyList()
            }.getOrDefault(emptyList())
        }

        return emptyList()
    }

    /**
     * Resolves the target remote directory for the drop.
     * Uses the currently selected directory in the tree, or falls back to the current path.
     */
    private fun resolveTargetDirectory(): String {
        val selected = remoteTree.getSelectedEntries()
        val dirEntry = selected.firstOrNull { it.isDirectory }
        return dirEntry?.path ?: remoteTree.getCurrentPath()
    }
}
