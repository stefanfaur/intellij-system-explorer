package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.github.stefanfaur.explorer.ui.FileTreeComponent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Standard Swing [TransferHandler] for the file tree.
 * Provides reliable drag-and-drop by using Swing's built-in DnD mechanism
 * rather than relying solely on IntelliJ's DnDManager.
 */
class FileTreeTransferHandler(
    private val fileTreeComponent: FileTreeComponent
) : TransferHandler() {

    companion object {
        private val LOG = Logger.getInstance(FileTreeTransferHandler::class.java)
    }

    override fun getSourceActions(c: JComponent): Int = COPY_OR_MOVE

    override fun createTransferable(c: JComponent): Transferable? {
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isEmpty()) return null
        val ioFiles = selected.map { java.io.File(it.path) }
        val pathsString = selected.joinToString("\n") { it.path }
        return object : Transferable {
            private val flavors = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)
            override fun getTransferDataFlavors() = flavors
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in flavors
            override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                DataFlavor.javaFileListFlavor -> ioFiles
                DataFlavor.stringFlavor -> pathsString
                else -> throw UnsupportedFlavorException(flavor)
            }
        }
    }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
        // In paste mode (not a drop), just check the flavor
        if (!support.isDrop) return true
        val tree = support.component as? JTree ?: return false
        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
        val path = dropLocation.path ?: return false
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
        val vf = node.userObject as? VirtualFile ?: return false
        // Can drop on directories or files (file = drop into parent dir)
        return true
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false

        val tree = support.component as? JTree ?: return false

        val targetDir: VirtualFile
        val isMove: Boolean
        if (support.isDrop) {
            val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
            val path = dropLocation.path ?: return false
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
            val targetVf = node.userObject as? VirtualFile ?: return false
            targetDir = if (targetVf.isDirectory) targetVf else targetVf.parent ?: return false
            isMove = support.dropAction == MOVE
        } else {
            // Paste mode: use currently selected node
            val selPath = tree.selectionPath ?: return false
            val node = selPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
            val targetVf = node.userObject as? VirtualFile ?: return false
            targetDir = if (targetVf.isDirectory) targetVf else targetVf.parent ?: return false
            isMove = false
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val ioFiles = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
            val vFiles = ioFiles.mapNotNull { file ->
                LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
            }
            val filesToDrop = vFiles.filter { it.parent != targetDir }
            if (filesToDrop.isEmpty()) return false

            for (file in filesToDrop) {
                try {
                    if (isMove) {
                        FileActions.moveTo(file, targetDir)
                    } else {
                        FileActions.copyTo(file, targetDir)
                    }
                } catch (e: Exception) {
                    LOG.warn("DnD: failed to ${if (isMove) "move" else "copy"} '${file.name}': ${e.message}", e)
                }
            }
            fileTreeComponent.refresh()
            return true
        } catch (e: Exception) {
            LOG.warn("DnD import failed: ${e.message}", e)
            return false
        }
    }

    override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
        if (action == MOVE) {
            fileTreeComponent.refresh()
        }
    }
}
