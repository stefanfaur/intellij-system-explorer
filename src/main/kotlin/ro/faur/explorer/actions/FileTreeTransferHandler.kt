package ro.faur.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import ro.faur.explorer.ui.FileTreeComponent
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
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

    override fun getSourceActions(c: JComponent): Int {
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isNotEmpty()) {
            val img = buildGhostImage(selected, c)
            setDragImage(img)
            setDragImageOffset(java.awt.Point(img.width / 2, img.height / 2))
        }
        return COPY_OR_MOVE
    }

    private fun buildGhostImage(files: List<VirtualFile>, component: JComponent): BufferedImage {
        val label = if (files.size == 1) files[0].name else "${files[0].name} (+${files.size - 1} more)"
        val icon = if (files.size == 1) (files[0].fileType.icon ?: AllIcons.FileTypes.Any_type)
                   else AllIcons.FileTypes.Any_type
        val fm = component.getFontMetrics(component.font)
        val w = (icon.iconWidth + 6 + fm.stringWidth(label)).coerceAtLeast(60)
        val h = (icon.iconHeight + 4).coerceAtLeast(20)
        val img = UIUtil.createImage(component, w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)
        icon.paintIcon(component, g, 2, (h - icon.iconHeight) / 2)
        g.color = component.foreground
        g.font = component.font
        g.drawString(label, icon.iconWidth + 6, h / 2 + fm.ascent / 2)
        g.dispose()
        return img
    }

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
                val existing = targetDir.findChild(file.name)
                if (existing != null) {
                    val result = Messages.showYesNoDialog(
                        null as com.intellij.openapi.project.Project?,
                        "'${file.name}' already exists in '${targetDir.name}'. Replace it?",
                        "Confirm Replace",
                        "Replace", "Skip",
                        Messages.getWarningIcon()
                    )
                    if (result != Messages.YES) continue
                    com.intellij.openapi.application.ApplicationManager.getApplication()
                        .runWriteAction { existing.delete(this@FileTreeTransferHandler) }
                }
                try {
                    if (isMove) {
                        FileActions.moveTo(file, targetDir)
                    } else {
                        FileActions.copyTo(file, targetDir)
                    }
                } catch (e: Exception) {
                    LOG.warn("DnD: failed to ${if (isMove) "move" else "copy"} '${file.name}': ${e.message}", e)
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Explorer.DnD")
                        .createNotification(
                            "Failed to ${if (isMove) "move" else "copy"} '${file.name}': ${e.message}",
                            NotificationType.WARNING
                        )
                        .notify(null)
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
            val selected = fileTreeComponent.getSelectedFiles()
            val sourceParents = selected.map { it.parent }.toSet()
            sourceParents.forEach { it?.refresh(false, false) }
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
