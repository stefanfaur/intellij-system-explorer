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
 *
 * Handles:
 * - Drag OUT from the explorer tree (via [createTransferable])
 * - Drop IN from external apps and IntelliJ panels that use AWT DnD (via [importData])
 * - Paste operations (via [importData] with `!support.isDrop`)
 *
 * Drops that don't land on a specific tree node fall back to the current root directory.
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
        // For drops: accept if we can resolve a target directory (node or root fallback)
        resolveDropTarget(support) ?: return false
        return true
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false

        val targetDir: VirtualFile
        val isMove: Boolean

        if (support.isDrop) {
            targetDir = resolveDropTarget(support) ?: return false
            isMove = support.dropAction == MOVE
        } else {
            // Paste mode: use currently selected node or root
            val tree = support.component as? JTree ?: return false
            targetDir = resolveSelectionTarget(tree) ?: return false
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

    /**
     * Resolves the target directory for a drop.
     * If the drop is on a tree node, uses that node's directory.
     * Otherwise falls back to the current root directory.
     */
    private fun resolveDropTarget(support: TransferSupport): VirtualFile? {
        val dropLocation = support.dropLocation as? JTree.DropLocation ?: return getRootDir()
        val path = dropLocation.path
        if (path != null) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            if (vf != null) {
                return if (vf.isDirectory) vf else vf.parent
            }
        }
        // Drop was in empty space — fall back to current root directory
        return getRootDir()
    }

    /**
     * Resolves the target directory from the current tree selection (for paste).
     * Falls back to the current root directory if nothing is selected.
     */
    private fun resolveSelectionTarget(tree: JTree): VirtualFile? {
        val selPath = tree.selectionPath
        if (selPath != null) {
            val node = selPath.lastPathComponent as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            if (vf != null) {
                return if (vf.isDirectory) vf else vf.parent
            }
        }
        return getRootDir()
    }

    private fun getRootDir(): VirtualFile? {
        val rootPath = fileTreeComponent.currentRootPath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(rootPath)
    }
}
