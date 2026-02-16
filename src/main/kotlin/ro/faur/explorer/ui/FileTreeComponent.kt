package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import ro.faur.explorer.actions.DragDropHandler
import ro.faur.explorer.actions.FileActions
import ro.faur.explorer.actions.FileTreeTransferHandler
import ro.faur.explorer.model.Bookmark
import ro.faur.explorer.model.BookmarkManager
import ro.faur.explorer.model.FileTreeModel
import ro.faur.explorer.settings.ExplorerSettings
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Wraps a [Tree] (IntelliJ's JTree subclass) showing filesystem entries.
 *
 * Uses a simple [DefaultTreeModel] with [DefaultMutableTreeNode] approach.
 * Each node's userObject is the [VirtualFile] it represents (except for placeholder nodes).
 * Directories are lazily expanded via [TreeWillExpandListener].
 *
 * Implements [Disposable] so that all registered listeners are cleaned up when the component
 * is disposed, preventing memory leaks.
 */
class FileTreeComponent(private val project: Project) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(FileTreeComponent::class.java)
    }

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode, true)
    val tree: JTree = Tree(treeModel)

    /** Called when a directory is double-clicked (navigate into it). */
    var onDirectoryDoubleClicked: ((VirtualFile) -> Unit)? = null

    /** Called when tree selection changes (for status bar updates, etc.). */
    var onSelectionChanged: (() -> Unit)? = null

    /** Called when files are modified (for ExplorerPanel to refresh). */
    var onFilesModified: (() -> Unit)? = null

    var showHidden: Boolean = false
    var filterPattern: String = ""

    /** Cached flag for whether to show file permissions (updated by ExplorerPanel). */
    internal var showPermissions: Boolean = false

    /** Cache for file permissions to avoid disk I/O on every render. */
    private val permissionsCache = mutableMapOf<String, String>()

    /** The path currently displayed as the tree root. */
    var currentRootPath: String? = null
        private set

    /** Files marked for move (cut). Cleared after paste or copy. */
    internal var cutFiles: List<VirtualFile>? = null

    // Store listener references for cleanup in dispose()
    private val expandListener: TreeWillExpandListener
    private val mouseListener: MouseAdapter
    private val selectionListener: TreeSelectionListener
    private val popupMouseListener: MouseAdapter
    private var dragDropHandler: DragDropHandler? = null

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = VirtualFileCellRenderer()

        @Suppress("DEPRECATION")
        TreeSpeedSearch(tree) { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            vf?.name ?: node?.userObject?.toString() ?: ""
        }

        // Lazy directory expansion
        expandListener = object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as DefaultMutableTreeNode
                val file = node.userObject as? VirtualFile ?: return
                if (file.isDirectory && node.childCount == 1 &&
                    (node.firstChild as? DefaultMutableTreeNode)?.userObject is String
                ) {
                    // Replace placeholder with actual children
                    node.removeAllChildren()
                    loadChildren(node, file)
                    treeModel.nodeStructureChanged(node)
                }
            }

            override fun treeWillCollapse(event: TreeExpansionEvent) {}
        }
        tree.addTreeWillExpandListener(expandListener)

        // Double-click handler
        mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val vf = node.userObject as? VirtualFile ?: return
                    if (vf.isDirectory) {
                        onDirectoryDoubleClicked?.invoke(vf)
                    } else {
                        FileEditorManager.getInstance(project).openFile(vf, true)
                    }
                }
            }
        }
        tree.addMouseListener(mouseListener)

        // Selection change listener
        selectionListener = TreeSelectionListener {
            onSelectionChanged?.invoke()
        }
        tree.addTreeSelectionListener(selectionListener)

        // Context menu (right-click popup)
        popupMouseListener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { showPopupIfNeeded(e) }
            override fun mouseReleased(e: MouseEvent) { showPopupIfNeeded(e) }

            private fun showPopupIfNeeded(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    // Select the node under cursor if not already selected
                    val path = tree.getPathForLocation(e.x, e.y)
                    if (path != null && !tree.isPathSelected(path)) {
                        tree.selectionPath = path
                    }
                    createPopupMenu().show(tree, e.x, e.y)
                }
            }
        }
        tree.addMouseListener(popupMouseListener)

        // Swing DnD handles drag-out (from our tree to IntelliJ panels / external apps).
        // tree.dragEnabled installs Swing's DragGestureRecognizer; TransferHandler
        // creates the Transferable. This is the most portable approach.
        tree.dragEnabled = true
        tree.transferHandler = FileTreeTransferHandler(this)
        tree.dropMode = javax.swing.DropMode.ON_OR_INSERT

        // Register as DnDTarget ONLY (not DnDSource) via IntelliJ's DnDManager.
        // This receives drops from IntelliJ-internal panels (Project View, etc.)
        // that use DnDManager for their drag operations.
        // We do NOT register as DnDSource because that conflicts with Swing's
        // DragGestureRecognizer — both systems listen for mouse-drag gestures
        // and they interfere with each other.
        try {
            val handler = DragDropHandler(this, project)
            dragDropHandler = handler
            DnDManager.getInstance().registerTarget(handler, tree)
        } catch (e: IllegalStateException) {
            LOG.debug("DnDManager not available, drag-and-drop disabled: ${e.message}")
        } catch (e: Exception) {
            LOG.debug("Failed to register drag-and-drop handler: ${e.message}")
        }
    }

    /**
     * Sets the root directory for the tree and populates the first level of children.
     */
    fun setRoot(path: String) {
        currentRootPath = path
        permissionsCache.clear() // Clear cache when changing directories
        rootNode.removeAllChildren()
        val dir = LocalFileSystem.getInstance().findFileByPath(path)
        if (dir != null && dir.isDirectory) {
            loadChildren(rootNode, dir)
        }
        treeModel.reload()
    }

    /**
     * Refreshes the tree by re-reading the current root.
     * Performs a shallow (non-recursive) VFS refresh of the current directory only.
     */
    fun refresh() {
        permissionsCache.clear() // Clear cache on refresh to get updated permissions
        currentRootPath?.let { path ->
            val dir = LocalFileSystem.getInstance().findFileByPath(path)
            // Shallow refresh: only the current directory, not recursive
            dir?.refresh(false, false)
            setRoot(path)
        }
    }

    /**
     * Returns all VirtualFile objects currently visible as top-level children of the root.
     */
    fun getRootChildren(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode
            val vf = child?.userObject as? VirtualFile
            if (vf != null) result.add(vf)
        }
        return result
    }

    /**
     * Returns the currently selected VirtualFile objects in the tree.
     */
    fun getSelectedFiles(): List<VirtualFile> {
        val paths = tree.selectionPaths ?: return emptyList()
        return paths.mapNotNull { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            node?.userObject as? VirtualFile
        }
    }

    // ---- Context menu ----

    /** The directory context for paste and new file/folder operations. */
    internal fun getContextDirectory(): VirtualFile? {
        val selected = getSelectedFiles().firstOrNull()
        return if (selected?.isDirectory == true) selected else selected?.parent
    }

    private fun createPopupMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val selected = getSelectedFiles()
        val singleFile = selected.singleOrNull()
        val contextDir = getContextDirectory()

        // Open (files only)
        if (singleFile != null && !singleFile.isDirectory) {
            menu.add(JMenuItem("Open").apply {
                addActionListener {
                    FileEditorManager.getInstance(project).openFile(singleFile, true)
                }
            })
        }

        // Open in System
        if (singleFile != null) {
            menu.add(JMenuItem("Open in System").apply {
                addActionListener {
                    java.awt.Desktop.getDesktop().open(java.io.File(singleFile.path))
                }
            })
        }

        menu.addSeparator()

        // Copy
        if (selected.isNotEmpty()) {
            menu.add(JMenuItem("Copy").apply {
                addActionListener {
                    FileActions.copyToClipboard(selected)
                    cutFiles = null // clear any pending cut
                }
            })
        }

        // Cut
        if (selected.isNotEmpty()) {
            menu.add(JMenuItem("Cut").apply {
                addActionListener {
                    FileActions.copyToClipboard(selected)
                    cutFiles = selected.toList() // mark for move on paste
                }
            })
        }

        // Paste
        if (contextDir != null) {
            menu.add(JMenuItem("Paste").apply {
                addActionListener { pasteFiles(contextDir) }
            })
        }

        // Copy Path
        if (singleFile != null) {
            menu.add(JMenuItem("Copy Path").apply {
                addActionListener { FileActions.copyPathToClipboard(singleFile) }
            })
        }

        menu.addSeparator()

        // Rename
        if (singleFile != null) {
            menu.add(JMenuItem("Rename").apply {
                addActionListener { renameFile(singleFile) }
            })
        }

        // Delete
        if (selected.isNotEmpty()) {
            menu.add(JMenuItem("Delete").apply {
                addActionListener { deleteFiles(selected) }
            })
        }

        menu.addSeparator()

        // New File
        if (contextDir != null) {
            menu.add(JMenuItem("New File").apply {
                addActionListener { createNewFile(contextDir) }
            })
        }

        // New Folder
        if (contextDir != null) {
            menu.add(JMenuItem("New Folder").apply {
                addActionListener { createNewFolder(contextDir) }
            })
        }

        menu.addSeparator()

        // Add to Bookmarks (directories only)
        if (singleFile != null && singleFile.isDirectory) {
            menu.add(JMenuItem("Add to Bookmarks").apply {
                addActionListener { addToBookmarks(singleFile) }
            })
        }

        // Refresh
        menu.add(JMenuItem("Refresh").apply {
            addActionListener { refresh(); onFilesModified?.invoke() }
        })

        return menu
    }

    internal fun pasteFiles(destDir: VirtualFile) {
        val cuts = cutFiles
        if (cuts != null) {
            // Move operation
            cuts.forEach { FileActions.moveTo(it, destDir) }
            cutFiles = null
        } else {
            // Copy from clipboard
            val clipboard = CopyPasteManager.getInstance()
            val transferable = clipboard.contents ?: return
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @Suppress("UNCHECKED_CAST")
                val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                files.filterIsInstance<java.io.File>().forEach { file ->
                    val vf = LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
                    if (vf != null) FileActions.copyTo(vf, destDir)
                }
            }
        }
        refresh()
        onFilesModified?.invoke()
    }

    internal fun renameFile(file: VirtualFile) {
        val newName = Messages.showInputDialog(
            project, "Enter new name:", "Rename", null, file.name, null
        )
        if (newName != null && newName.isNotBlank() && newName != file.name) {
            FileActions.rename(file, newName)
            refresh()
            onFilesModified?.invoke()
        }
    }

    internal fun deleteFiles(files: List<VirtualFile>) {
        var toTrash = false
        try {
            val settings = ExplorerSettings.getInstance()
            toTrash = settings.state.deleteToTrash
            if (settings.state.confirmDelete) {
                val action = if (toTrash) "Move to Trash" else "Permanently delete"
                val itemDesc = if (files.size == 1) "'${files[0].name}'" else "${files.size} items"
                val message = "$action $itemDesc?"
                val title = if (toTrash) "Move to Trash" else "Confirm Delete"
                val result = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())
                if (result != Messages.YES) return
            }
        } catch (_: Exception) {
            // Settings service might not be available in tests; proceed without confirmation
        }
        files.forEach { FileActions.delete(it, toTrash) }
        refresh()
        onFilesModified?.invoke()
    }

    private fun createNewFile(parentDir: VirtualFile) {
        val name = Messages.showInputDialog(project, "Enter file name:", "New File", null)
        if (name != null && name.isNotBlank()) {
            val created = FileActions.createFile(parentDir, name)
            if (created == null) {
                Messages.showWarningDialog(project, "A file with the name '$name' already exists.", "File Already Exists")
                return
            }
            refresh()
            onFilesModified?.invoke()
        }
    }

    private fun createNewFolder(parentDir: VirtualFile) {
        val name = Messages.showInputDialog(project, "Enter folder name:", "New Folder", null)
        if (name != null && name.isNotBlank()) {
            val created = FileActions.createFolder(parentDir, name)
            if (created == null) {
                Messages.showWarningDialog(project, "A folder with the name '$name' already exists.", "Folder Already Exists")
                return
            }
            refresh()
            onFilesModified?.invoke()
        }
    }

    internal fun addToBookmarks(dir: VirtualFile) {
        try {
            val manager = BookmarkManager.getInstance()
            manager.addBookmark(Bookmark(dir.name, dir.path))
            onFilesModified?.invoke()
        } catch (_: Exception) {
            // Service might not be available in tests
        }
    }

    private fun loadChildren(parentNode: DefaultMutableTreeNode, parentFile: VirtualFile) {
        val model = FileTreeModel(showHidden = showHidden, foldersFirst = true)
        val children = if (filterPattern.isNotBlank()) {
            model.getFilteredChildren(parentFile, filterPattern)
        } else {
            model.getChildren(parentFile)
        }

        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            if (child.isDirectory) {
                // Add a placeholder child so the node is expandable
                childNode.add(DefaultMutableTreeNode("loading..."))
            } else {
                childNode.allowsChildren = false
            }
            parentNode.add(childNode)
        }
    }

    /**
     * Removes all listeners registered on the tree to prevent memory leaks.
     */
    override fun dispose() {
        tree.removeTreeWillExpandListener(expandListener)
        tree.removeMouseListener(mouseListener)
        tree.removeMouseListener(popupMouseListener)
        tree.removeTreeSelectionListener(selectionListener)

        // Unregister DnDTarget (source is not registered — Swing handles drag-out)
        try {
            val handler = dragDropHandler
            if (handler != null) {
                DnDManager.getInstance().unregisterTarget(handler, tree)
                dragDropHandler = null
            }
        } catch (e: IllegalStateException) {
            LOG.debug("DnDManager not available during dispose: ${e.message}")
        } catch (e: Exception) {
            LOG.debug("Failed to unregister drag-and-drop handler: ${e.message}")
        }
    }

    /**
     * Custom cell renderer that displays VirtualFile names with appropriate icons.
     *
     * Uses IntelliJ's [ColoredTreeCellRenderer] which integrates with IntelliJ's
     * Tree painting and handles backgrounds/opacity correctly.
     */
    private inner class VirtualFileCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            if (vf != null) {
                icon = getIconForFile(vf)

                // Add file permissions if enabled (right-aligned)
                // Use cached flag to avoid expensive getInstance() calls on every cell render
                if (showPermissions) {
                    val perms = getCachedPermissions(vf)
                    if (perms.isNotEmpty()) {
                        // Calculate available width for filename
                        val treeWidth = tree.visibleRect.width // Use visible area, not total width
                        val permissionsWidth = 120 // Reserve space for permissions (drwxr-xr-x = ~100px)
                        val margin = 20 // Extra margin for safety
                        val availableWidth = treeWidth - permissionsWidth - margin

                        // Manually truncate filename if needed
                        val fm = getFontMetrics(font)
                        val truncatedName = truncateString(vf.name, fm, availableWidth)

                        // Append truncated filename
                        append(truncatedName)

                        // Add padding to push permissions to the right
                        appendTextPadding(treeWidth - permissionsWidth)

                        // Append permissions
                        append(perms, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    } else {
                        // No permissions to show, just append the filename
                        append(vf.name)
                    }
                } else {
                    // Permissions not enabled, just append the filename
                    append(vf.name)
                }
            } else {
                append(value?.toString() ?: "")
            }
        }

        /**
         * Truncates a string to fit within the specified width, adding "..." if necessary.
         * Uses FontMetrics to accurately measure string width.
         */
        private fun truncateString(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
            if (maxWidth <= 0) return text

            val textWidth = fm.stringWidth(text)
            if (textWidth <= maxWidth) return text

            val ellipsis = "..."
            val ellipsisWidth = fm.stringWidth(ellipsis)
            val availableWidth = maxWidth - ellipsisWidth

            if (availableWidth <= 0) return ellipsis

            // Binary search to find the optimal truncation point
            var low = 0
            var high = text.length
            var result = text

            while (low <= high) {
                val mid = (low + high) / 2
                val truncated = text.substring(0, mid)
                val truncatedWidth = fm.stringWidth(truncated)

                if (truncatedWidth <= availableWidth) {
                    result = truncated + ellipsis
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            return result
        }

        private fun getIconForFile(vf: VirtualFile): Icon {
            return if (vf.isDirectory) AllIcons.Nodes.Folder
            else vf.fileType.icon ?: AllIcons.FileTypes.Any_type
        }

        /**
         * Gets cached permissions for a file, computing and caching if not present.
         * This avoids expensive disk I/O on every cell render.
         */
        private fun getCachedPermissions(file: VirtualFile): String {
            return permissionsCache.getOrPut(file.path) {
                formatPermissions(file)
            }
        }

        private fun formatPermissions(file: VirtualFile): String {
            if (SystemInfo.isWindows) return ""

            try {
                val path = Paths.get(file.path)
                val perms = Files.getPosixFilePermissions(path)
                val permString = PosixFilePermissions.toString(perms)
                val prefix = if (file.isDirectory) "d" else "-"
                return "$prefix$permString"
            } catch (_: Exception) {
                return ""  // Fail silently on any error
            }
        }
    }
}
