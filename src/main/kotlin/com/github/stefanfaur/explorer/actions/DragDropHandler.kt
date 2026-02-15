package com.github.stefanfaur.explorer.actions

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSource
import com.intellij.ide.dnd.DnDTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.github.stefanfaur.explorer.ui.FileTreeComponent
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Provides drag-and-drop support for the [FileTreeComponent]'s JTree.
 *
 * Implements both [DnDSource] (dragging FROM the explorer tree) and
 * [DnDTarget] (dropping INTO the explorer tree).
 *
 * Behavior:
 * - FROM Explorer tree TO elsewhere = copy files
 * - FROM elsewhere TO Explorer tree = copy files into the target directory
 * - Hold Shift while dropping = move instead of copy
 */
class DragDropHandler(
    private val fileTreeComponent: FileTreeComponent,
    private val project: Project
) : DnDSource, DnDTarget {

    private val tree get() = fileTreeComponent.tree

    companion object {
        private val LOG = Logger.getInstance(DragDropHandler::class.java)
    }

    // ---- DnDSource ----

    override fun canStartDragging(action: DnDAction, dragOrigin: Point): Boolean {
        return fileTreeComponent.getSelectedFiles().isNotEmpty()
    }

    override fun startDragging(action: DnDAction, dragOrigin: Point): DnDDragStartBean? {
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isEmpty()) return null
        val transferable = createTransferable(selected)
        return DnDDragStartBean(transferable)
    }

    override fun dropActionChanged(gestureModifiers: Int) {
        // No action needed; shift detection is handled at drop time
    }

    // ---- DnDTarget ----

    override fun update(event: DnDEvent): Boolean {
        val point = event.point ?: return false
        val node = getNodeAtPoint(point) ?: return false
        val targetDir = resolveTargetDirectory(node)
        if (targetDir != null) {
            event.setDropPossible(true)
            return true
        }
        event.setDropPossible(false)
        return false
    }

    override fun drop(event: DnDEvent) {
        val point = event.point ?: return
        val node = getNodeAtPoint(point) ?: return
        val targetDir = resolveTargetDirectory(node) ?: return

        // Determine if this is a move (shift held) or copy
        val isMove = event.action == DnDAction.MOVE

        // Extract files from the event's attached object
        val files = extractFiles(event.attachedObject)
        if (files.isEmpty()) return

        // Filter out files that already reside in the target directory
        val filesToDrop = files.filter { it.parent != targetDir }
        if (filesToDrop.isEmpty()) return

        performDrop(filesToDrop, targetDir, isMove)
        fileTreeComponent.refresh()
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
     *
     * @param files the files to copy/move
     * @param targetDir the destination directory
     * @param isMove true to move files, false to copy
     */
    fun performDrop(files: List<VirtualFile>, targetDir: VirtualFile, isMove: Boolean) {
        for (file in files) {
            // Skip files that already reside in the target directory
            if (file.parent == targetDir) continue

            try {
                if (isMove) {
                    FileActions.moveTo(file, targetDir)
                } else {
                    FileActions.copyTo(file, targetDir)
                }
            } catch (e: Exception) {
                val action = if (isMove) "move" else "copy"
                LOG.warn("Failed to $action '${file.name}' to '${targetDir.path}': ${e.message}", e)
            }
        }
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

    /**
     * Gets the tree node at the given point coordinates.
     * Returns null if the point is not directly over a tree node,
     * which correctly rejects drops in empty space.
     */
    private fun getNodeAtPoint(point: Point): DefaultMutableTreeNode? {
        val path: TreePath = tree.getPathForLocation(point.x, point.y) ?: return null
        return path.lastPathComponent as? DefaultMutableTreeNode
    }

    /**
     * Extracts [VirtualFile] objects from the DnD attached object.
     *
     * Handles three cases:
     * 1. Attached object is a [Transferable] with javaFileListFlavor (from our own drag source)
     * 2. Attached object is a List<VirtualFile> (legacy or internal)
     * 3. Attached object is a List<java.io.File> (from external sources)
     */
    private fun extractFiles(attachedObject: Any?): List<VirtualFile> {
        if (attachedObject == null) return emptyList()

        // Handle Transferable (from our own drag source via createTransferable)
        if (attachedObject is Transferable) {
            if (attachedObject.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val ioFiles = attachedObject.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
                    val vFiles = ioFiles.mapNotNull { file ->
                        LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                    }
                    if (vFiles.isNotEmpty()) return vFiles
                } catch (e: Exception) {
                    LOG.debug("Failed to extract files from Transferable: ${e.message}")
                }
            }
        }

        if (attachedObject is List<*>) {
            // Check if it's VirtualFiles (from our drag source)
            val virtualFiles = attachedObject.filterIsInstance<VirtualFile>()
            if (virtualFiles.isNotEmpty()) return virtualFiles

            // Check if it's java.io.Files (from external sources)
            val ioFiles = attachedObject.filterIsInstance<java.io.File>()
            if (ioFiles.isNotEmpty()) {
                return ioFiles.mapNotNull { file ->
                    LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                }
            }
        }

        return emptyList()
    }
}
