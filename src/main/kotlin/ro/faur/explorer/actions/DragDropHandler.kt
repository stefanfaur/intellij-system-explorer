package ro.faur.explorer.actions

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.FileFlavorProvider
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import ro.faur.explorer.ui.FileTreeComponent
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Handles drops INTO the explorer tree from IntelliJ-internal panels
 * (Project View, editor, etc.) that use IntelliJ's [DnDManager] system,
 * as well as native drops from external applications.
 *
 * Implements [DnDNativeTarget] (extends [DnDTarget]) so that both
 * DnDManager-initiated drags and native AWT DnD drags are received.
 *
 * Drag-out from the explorer is handled by Swing's [TransferHandler] via
 * [ro.faur.explorer.actions.FileTreeTransferHandler] — the two systems are intentionally separated
 * because registering both a [DnDSource] and Swing's drag gesture recognizer
 * on the same component causes them to conflict over mouse events.
 *
 * Behavior:
 * - FROM IntelliJ panels TO Explorer tree = copy files into target directory
 * - Hold Shift while dropping = move instead of copy
 */
class DragDropHandler(
    private val fileTreeComponent: FileTreeComponent,
    private val project: Project
) : DnDNativeTarget {

    private val tree get() = fileTreeComponent.tree

    companion object {
        private val LOG = Logger.getInstance(DragDropHandler::class.java)
    }

    // ---- DnDTarget ----

    override fun update(event: DnDEvent): Boolean {
        val point = event.point
        val targetDir = resolveDropTarget(point)

        // Compute which row should be highlighted (-1 means no highlight)
        val highlightRow: Int = if (targetDir != null && point != null) {
            val node = getNodeAtPoint(point)
            if (node != null) {
                val path = tree.getPathForLocation(point.x, point.y)
                if (path != null) tree.getRowForPath(path) else -1
            } else -1
        } else -1

        val currentRow = tree.getClientProperty("dnd.hoveredRow") as? Int ?: -1
        if (highlightRow != currentRow) {
            tree.putClientProperty("dnd.hoveredRow", highlightRow)
            tree.repaint()
        }

        if (targetDir != null) {
            event.setDropPossible(true)
            return true
        }
        event.setDropPossible(false)
        return false
    }

    override fun drop(event: DnDEvent) {
        // Clear the hover highlight before processing the drop
        tree.putClientProperty("dnd.hoveredRow", -1)
        tree.repaint()

        val targetDir = resolveDropTarget(event.point) ?: return
        val isMove = event.action == DnDAction.MOVE

        val files = extractFiles(event)
        if (files.isEmpty()) {
            LOG.debug("Drop: no files could be extracted from event")
            return
        }

        val filesToDrop = files.filter { it.parent != targetDir }
        if (filesToDrop.isEmpty()) return

        performDrop(filesToDrop, targetDir, isMove)

        // Refresh source parent directories after a move (DND-06)
        if (isMove) {
            val sourceParents = filesToDrop.map { it.parent }.toSet()
            sourceParents.forEach { it?.refresh(false, false) }
        }

        fileTreeComponent.refresh()
    }

    /**
     * Resolves the target directory for a drop at the given point.
     * If the point is over a tree node, uses that node's directory.
     * Otherwise falls back to the current root directory.
     */
    private fun resolveDropTarget(point: Point?): VirtualFile? {
        if (point != null) {
            val node = getNodeAtPoint(point)
            if (node != null) {
                val dir = resolveTargetDirectory(node)
                if (dir != null) return dir
            }
        }
        // Fall back to current root directory
        val rootPath = fileTreeComponent.currentRootPath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(rootPath)
    }

    // ---- Internal/testable methods ----

    /**
     * Resolves the target directory for a drop operation given a tree node.
     * If the node is a directory, returns it directly.
     * If the node is a file, returns its parent directory.
     */
    fun resolveTargetDirectory(node: DefaultMutableTreeNode): VirtualFile? {
        val vf = node.userObject as? VirtualFile ?: return null
        return if (vf.isDirectory) vf else vf.parent
    }

    /**
     * Performs the actual file copy or move operation.
     *
     * Files that already reside in the target directory are skipped.
     * Each file operation is wrapped in a try-catch so that one failure
     * does not abort the entire batch.
     */
    fun performDrop(files: List<VirtualFile>, targetDir: VirtualFile, isMove: Boolean) {
        for (file in files) {
            if (file.parent == targetDir) continue
            val existing = targetDir.findChild(file.name)
            if (existing != null) {
                val result = Messages.showYesNoDialog(
                    project,
                    "'${file.name}' already exists in '${targetDir.name}'. Replace it?",
                    "Confirm Replace",
                    "Replace", "Skip",
                    Messages.getWarningIcon()
                )
                if (result != Messages.YES) continue
                ApplicationManager.getApplication()
                    .runWriteAction { existing.delete(this) }
            }
            try {
                if (isMove) {
                    FileActions.moveTo(file, targetDir)
                } else {
                    FileActions.copyTo(file, targetDir)
                }
            } catch (e: Exception) {
                val action = if (isMove) "move" else "copy"
                LOG.warn("Failed to $action '${file.name}' to '${targetDir.path}': ${e.message}", e)
                notifyError("Failed to $action '${file.name}': ${e.message}")
            }
        }
    }

    private fun notifyError(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Explorer.DnD")
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * Creates a [Transferable] wrapping the given [VirtualFile] list as
     * [DataFlavor.javaFileListFlavor] for interoperability with other
     * IntelliJ components and the system clipboard.
     */
    fun createTransferable(files: List<VirtualFile>): Transferable {
        val ioFiles = files.map { java.io.File(it.path) }
        val pathsString = files.joinToString("\n") { it.path }
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

    // ---- Private helpers ----

    private fun getNodeAtPoint(point: Point): DefaultMutableTreeNode? {
        val path: TreePath = tree.getPathForLocation(point.x, point.y) ?: return null
        return path.lastPathComponent as? DefaultMutableTreeNode
    }

    /**
     * Extracts [VirtualFile] objects from a [DnDEvent].
     *
     * Handles multiple data formats in priority order:
     * 1. [TransferableWrapper] — IntelliJ's Project View sends this via DnDManager.
     *    It does NOT extend [Transferable]; it extends [FileFlavorProvider].
     * 2. [FileFlavorProvider] — any object that can provide a file list.
     * 3. [Transferable] with javaFileListFlavor — from Swing DnD or clipboard.
     * 4. [DnDNativeTarget.EventInfo] — raw AWT transferable from native drops.
     * 5. Direct List<VirtualFile> or List<java.io.File>.
     */
    private fun extractFiles(event: DnDEvent): List<VirtualFile> {
        val attachedObject = event.attachedObject

        if (attachedObject == null) {
            LOG.debug("extractFiles: attachedObject is null")
            return tryNativeEventInfo(event)
        }

        LOG.debug("extractFiles: attachedObject type = ${attachedObject.javaClass.name}")

        // 1. TransferableWrapper — from Project View and other IntelliJ tree panels.
        //    Provides getPsiElements() and asFileList() directly.
        //    Does NOT implement Transferable (extends FileFlavorProvider instead).
        if (attachedObject is TransferableWrapper) {
            LOG.debug("extractFiles: handling as TransferableWrapper")
            val result = extractFromTransferableWrapper(attachedObject)
            if (result.isNotEmpty()) return result
        }

        // 2. FileFlavorProvider — anything that can provide a file list.
        if (attachedObject is FileFlavorProvider) {
            LOG.debug("extractFiles: handling as FileFlavorProvider")
            val result = extractFromFileFlavorProvider(attachedObject)
            if (result.isNotEmpty()) return result
        }

        // 3. Transferable with standard data flavors.
        if (attachedObject is Transferable) {
            LOG.debug("extractFiles: handling as Transferable")
            val result = extractFromTransferable(attachedObject)
            if (result.isNotEmpty()) return result
        }

        // 4. Direct list of VirtualFiles or java.io.Files.
        if (attachedObject is List<*>) {
            LOG.debug("extractFiles: handling as List")
            val virtualFiles = attachedObject.filterIsInstance<VirtualFile>()
            if (virtualFiles.isNotEmpty()) return virtualFiles

            val ioFiles = attachedObject.filterIsInstance<java.io.File>()
            if (ioFiles.isNotEmpty()) {
                return ioFiles.mapNotNull { file ->
                    LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                }
            }
        }

        // 5. Native event info fallback (for external app drops via DnDNativeTarget).
        val nativeResult = tryNativeEventInfo(event)
        if (nativeResult.isNotEmpty()) return nativeResult

        LOG.debug("extractFiles: could not extract any files from ${attachedObject.javaClass.name}")
        return emptyList()
    }

    /**
     * Extracts files from [TransferableWrapper] (sent by IntelliJ's Project View).
     * Tries PSI elements first (more precise), then falls back to file list.
     */
    private fun extractFromTransferableWrapper(wrapper: TransferableWrapper): List<VirtualFile> {
        // Try getPsiElements() — this is the richest data source
        try {
            val psiElements = wrapper.psiElements
            if (psiElements != null && psiElements.isNotEmpty()) {
                val vFiles = psiElements
                    .filterIsInstance<PsiFileSystemItem>()
                    .mapNotNull { it.virtualFile }
                if (vFiles.isNotEmpty()) {
                    LOG.debug("extractFromTransferableWrapper: got ${vFiles.size} files from PsiElements")
                    return vFiles
                }
            }
        } catch (e: Exception) {
            LOG.debug("extractFromTransferableWrapper: getPsiElements() failed: ${e.message}")
        }

        // Fall back to asFileList()
        return extractFromFileFlavorProvider(wrapper)
    }

    /**
     * Extracts files from any [FileFlavorProvider] via its asFileList() method.
     */
    private fun extractFromFileFlavorProvider(provider: FileFlavorProvider): List<VirtualFile> {
        try {
            val fileList = provider.asFileList()
            if (fileList != null && fileList.isNotEmpty()) {
                val vFiles = fileList.mapNotNull { file ->
                    LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                }
                if (vFiles.isNotEmpty()) {
                    LOG.debug("extractFromFileFlavorProvider: got ${vFiles.size} files from asFileList()")
                    return vFiles
                }
            }
        } catch (e: Exception) {
            LOG.debug("extractFromFileFlavorProvider: asFileList() failed: ${e.message}")
        }
        return emptyList()
    }

    /**
     * Extracts files from a standard [Transferable] using javaFileListFlavor
     * or by searching for PsiElement data flavors.
     */
    private fun extractFromTransferable(transferable: Transferable): List<VirtualFile> {
        // Try javaFileListFlavor first (most common for Swing sources)
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                @Suppress("UNCHECKED_CAST")
                val ioFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
                val vFiles = ioFiles.mapNotNull { file ->
                    LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                }
                if (vFiles.isNotEmpty()) return vFiles
            } catch (e: Exception) {
                LOG.debug("extractFromTransferable: javaFileListFlavor failed: ${e.message}")
            }
        }

        // Try extracting PsiElements from data flavors
        return extractPsiFilesFromFlavors(transferable)
    }

    /**
     * Searches a [Transferable]'s data flavors for PsiElement arrays.
     * This is a fallback for Transferable objects that carry PSI data
     * but aren't [TransferableWrapper] instances.
     */
    private fun extractPsiFilesFromFlavors(transferable: Transferable): List<VirtualFile> {
        try {
            for (flavor in transferable.transferDataFlavors) {
                if (flavor.representationClass?.name?.contains("PsiElement") == true ||
                    flavor.humanPresentableName.contains("PsiElement", ignoreCase = true)
                ) {
                    try {
                        val data = transferable.getTransferData(flavor)
                        if (data is Array<*>) {
                            return data.filterIsInstance<PsiFileSystemItem>()
                                .mapNotNull { it.virtualFile }
                        }
                    } catch (e: Exception) {
                        LOG.debug("extractPsiFilesFromFlavors: flavor ${flavor.humanPresentableName} failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("extractPsiFilesFromFlavors: failed to inspect flavors: ${e.message}")
        }
        return emptyList()
    }

    /**
     * Attempts to extract files from [DnDNativeTarget.EventInfo] attached to the event.
     * This handles drops from external applications (Finder, etc.) where the data
     * arrives as a native AWT transferable rather than a DnDManager attached object.
     */
    private fun tryNativeEventInfo(event: DnDEvent): List<VirtualFile> {
        try {
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val key = Key.findKeyByName(DnDNativeTarget.EVENT_KEY) as? Key<DnDNativeTarget.EventInfo>
            if (key != null) {
                val eventInfo = event.getUserData(key)
                if (eventInfo != null) {
                    val transferable = eventInfo.transferable
                    if (transferable != null) {
                        LOG.debug("tryNativeEventInfo: found native EventInfo with transferable")
                        return extractFromTransferable(transferable)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("tryNativeEventInfo: failed: ${e.message}")
        }
        return emptyList()
    }
}
