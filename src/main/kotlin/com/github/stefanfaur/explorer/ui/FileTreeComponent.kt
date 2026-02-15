package com.github.stefanfaur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.github.stefanfaur.explorer.actions.DragDropHandler
import com.github.stefanfaur.explorer.actions.FileActions
import com.github.stefanfaur.explorer.model.Bookmark
import com.github.stefanfaur.explorer.model.BookmarkManager
import com.github.stefanfaur.explorer.model.FileTreeModel
import com.github.stefanfaur.explorer.settings.ExplorerSettings
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
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
    private val treeModel = DefaultTreeModel(rootNode)
    val tree: JTree = Tree(treeModel)

    /** Called when a directory is double-clicked (navigate into it). */
    var onDirectoryDoubleClicked: ((VirtualFile) -> Unit)? = null

    /** Called when tree selection changes (for status bar updates, etc.). */
    var onSelectionChanged: (() -> Unit)? = null

    /** Called when files are modified (for ExplorerPanel to refresh). */
    var onFilesModified: (() -> Unit)? = null

    var showHidden: Boolean = false
    var filterPattern: String = ""

    private var currentRootPath: String? = null

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

        // Register drag-and-drop handler
        try {
            val handler = DragDropHandler(this, project)
            dragDropHandler = handler
            val dndManager = DnDManager.getInstance()
            dndManager.registerSource(handler, tree)
            dndManager.registerTarget(handler, tree)
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
        rootNode.removeAllChildren()
        val dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
        if (dir != null && dir.isDirectory) {
            loadChildren(rootNode, dir)
        }
        treeModel.reload()
    }

    /**
     * Refreshes the tree by re-reading the current root.
     */
    fun refresh() {
        currentRootPath?.let { setRoot(it) }
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

    private fun addToBookmarks(dir: VirtualFile) {
        try {
            val manager = BookmarkManager.getInstance()
            manager.addBookmark(Bookmark(dir.name, dir.path))
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

        // Unregister drag-and-drop handler
        try {
            val handler = dragDropHandler
            if (handler != null) {
                val dndManager = DnDManager.getInstance()
                dndManager.unregisterSource(handler, tree)
                dndManager.unregisterTarget(handler, tree)
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
     */
    private inner class VirtualFileCellRenderer : DefaultTreeCellRenderer() {

        init {
            // Prevent DefaultTreeCellRenderer from painting its own background
            // on non-selected items. Without this, every row gets a visible
            // background rectangle that clashes with the tree's native L&F.
            isOpaque = false
            backgroundNonSelectionColor = null
        }

        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            if (vf != null) {
                text = vf.name
                icon = getIconForFile(vf, expanded)
            }
            return component
        }

        private fun getIconForFile(vf: VirtualFile, expanded: Boolean): Icon {
            return if (vf.isDirectory) {
                AllIcons.Nodes.Folder
            } else {
                vf.fileType.icon ?: AllIcons.FileTypes.Any_type
            }
        }
    }
}
