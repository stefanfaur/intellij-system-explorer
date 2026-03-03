package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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

    /** Called on the EDT after the root directory finishes loading its children. */
    var onRootLoaded: (() -> Unit)? = null

    var showHidden: Boolean = false
    var filterPattern: String = ""

    /** Cached flag for whether to show file permissions (updated by ExplorerPanel). */
    internal var showPermissions: Boolean = false

    /** When true, directories show their child count in the tree (e.g. "src (12)"). */
    var showFolderItemCount: Boolean = false

    /** Cache for file permissions to avoid disk I/O on every render. */
    private val permissionsCache = ConcurrentHashMap<String, String>()

    /** Cache for file icons to avoid flashing during dumb mode transitions.
     *  When IntelliJ enters/exits dumb mode, vf.fileType returns different results,
     *  causing all icons to change twice in rapid succession → visible flash. */
    private val iconCache = ConcurrentHashMap<String, Icon>()

    /** Cache for VCS status colors (files and directories).
     *  Optional<Color> distinguishes "no color" (empty Optional) from "not yet computed" (absent key).
     *  Populated asynchronously off EDT; invalidated on FileStatusListener callbacks, setRoot(), refresh(). */
    private val vcsColorCache = ConcurrentHashMap<String, java.util.Optional<Color>>()

    /** Paths currently being looked up on a background thread. Prevents thundering-herd re-dispatch
     *  when the same file is rendered multiple times before the background task completes. */
    private val vcsColorPending = ConcurrentHashMap.newKeySet<String>()

    /** The path currently displayed as the tree root. */
    var currentRootPath: String? = null
        private set

    /**
     * Monotonically increasing counter for root-load operations.
     * Each [setRoot] call increments this; background callbacks discard results
     * that don't match the latest generation, preventing stale updates.
     */
    private val loadGeneration = AtomicInteger(0)

    /** Set to true in [dispose]; background callbacks check this before touching the model. */
    @Volatile private var isDisposed = false

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

        TreeUIHelper.getInstance().installTreeSpeedSearch(
            tree,
            com.intellij.util.containers.Convertor { path: javax.swing.tree.TreePath ->
                val node = path.lastPathComponent as? DefaultMutableTreeNode
                val vf = node?.userObject as? VirtualFile
                vf?.name ?: node?.userObject?.toString() ?: ""
            },
            true
        )

        // Subscribe to VCS status changes — FileStatusManager.addFileStatusListener auto-disconnects
        // when the given Disposable (this) is disposed.
        FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
            override fun fileStatusChanged(virtualFile: VirtualFile) {
                vcsColorCache.clear()
                vcsColorPending.clear()
                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) tree.repaint()
                }
            }
            override fun fileStatusesChanged() {
                vcsColorCache.clear()
                vcsColorPending.clear()
                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) tree.repaint()
                }
            }
        }, this)

        // Subscribe to dumb-mode transitions to invalidate the icon cache on smart-mode entry.
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                iconCache.clear()
                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) tree.repaint()
                }
            }
        })

        // Lazy directory expansion — VFS read happens off the EDT to avoid SlowOperations errors.
        expandListener = object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as DefaultMutableTreeNode
                val file = node.userObject as? VirtualFile ?: return
                if (!file.isDirectory || node.childCount != 1 ||
                    (node.firstChild as? DefaultMutableTreeNode)?.userObject !is String
                ) return

                // Snapshot mutable state before leaving the EDT.
                val capturedHidden = showHidden
                val capturedFilter = filterPattern
                val capturedRoot = currentRootPath

                ApplicationManager.getApplication().executeOnPooledThread {
                    val children = ReadAction.compute<List<VirtualFile>, Throwable> {
                        val model = FileTreeModel(showHidden = capturedHidden, foldersFirst = true)
                        if (capturedFilter.isNotBlank()) model.getFilteredChildren(file, capturedFilter)
                        else model.getChildren(file)
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (isDisposed || currentRootPath != capturedRoot) return@invokeLater
                        // Guard: skip if the node was already populated by a concurrent expansion.
                        if (node.childCount != 1 ||
                            (node.firstChild as? DefaultMutableTreeNode)?.userObject !is String
                        ) return@invokeLater
                        node.removeAllChildren()
                        populateNode(node, children)
                        treeModel.nodeStructureChanged(node)
                    }
                }
            }

            override fun treeWillCollapse(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val file = node.userObject as? VirtualFile ?: return
                if (!file.isDirectory) return
                // Restore "loading..." placeholder so treeWillExpand guard fires correctly on re-expansion.
                // invokeLater defers the model change until after Swing's collapse processing completes,
                // avoiding ConcurrentModificationException and visual artifacts.
                ApplicationManager.getApplication().invokeLater {
                    if (isDisposed) return@invokeLater
                    node.removeAllChildren()
                    node.add(DefaultMutableTreeNode("loading..."))
                    treeModel.nodeStructureChanged(node)
                }
            }
        }
        tree.addTreeWillExpandListener(expandListener)

        // Double-click handler
        mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val vf = node.userObject as? VirtualFile ?: return
                    openEntry(vf)
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
     *
     * Clears the tree immediately on the EDT (no flicker), then reads the directory
     * entries on a background thread to avoid VFS slow-operation errors, and
     * repopulates the model back on the EDT.  Stale background results are discarded
     * via a monotonic [loadGeneration] counter.
     */
    fun setRoot(path: String) {
        currentRootPath = path
        permissionsCache.clear()
        iconCache.clear()
        vcsColorCache.clear()
        vcsColorPending.clear()
        rootNode.removeAllChildren()
        treeModel.reload()   // show empty tree immediately

        val dir = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        if (!dir.isDirectory) return

        // Snapshot mutable state and grab a generation token before leaving the EDT.
        val gen = loadGeneration.incrementAndGet()
        val capturedHidden = showHidden
        val capturedFilter = filterPattern

        ApplicationManager.getApplication().executeOnPooledThread {
            val children = ReadAction.compute<List<VirtualFile>, Throwable> {
                val model = FileTreeModel(showHidden = capturedHidden, foldersFirst = true)
                if (capturedFilter.isNotBlank()) model.getFilteredChildren(dir, capturedFilter)
                else model.getChildren(dir)
            }
            // Pre-populate the permissions cache on the background thread to avoid disk I/O on the EDT.
            val newPermCache = mutableMapOf<String, String>()
            children.forEach { vf ->
                newPermCache[vf.path] = runCatching { formatPermissionsForFile(vf) }.getOrDefault("")
            }
            ApplicationManager.getApplication().invokeLater {
                if (isDisposed || loadGeneration.get() != gen) return@invokeLater
                permissionsCache.clear()
                permissionsCache.putAll(newPermCache)
                populateNode(rootNode, children)
                treeModel.reload()
                onRootLoaded?.invoke()
            }
        }
    }

    /**
     * Refreshes the tree by re-reading the current root.
     * Performs a shallow (non-recursive) VFS refresh of the current directory only.
     */
    fun refresh() {
        permissionsCache.clear()
        iconCache.clear()
        vcsColorCache.clear()
        vcsColorPending.clear()
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

    /**
     * Opens the current selection when exactly one node is selected.
     * Files are opened in editor; directories navigate into that directory.
     */
    fun openSelected() {
        val selected = getSelectedFiles().singleOrNull() ?: return
        openEntry(selected)
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

        // --- Group 1: Open actions ---

        // Open (visible for all, enabled for files only)
        menu.add(JMenuItem("Open").apply {
            isEnabled = singleFile != null && !singleFile.isDirectory
            addActionListener {
                if (singleFile != null) openEntry(singleFile)
            }
        })

        // Open in System
        menu.add(JMenuItem("Open in System").apply {
            isEnabled = singleFile != null &&
                    java.awt.Desktop.isDesktopSupported() &&
                    java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)
            addActionListener {
                if (singleFile != null) {
                    AppExecutorUtil.getAppExecutorService().execute {
                        runCatching { java.awt.Desktop.getDesktop().open(java.io.File(singleFile.path)) }
                            .onFailure { LOG.warn("Failed to open file in system: ${it.message}") }
                    }
                }
            }
        })

        // Reveal in Finder / Explorer / File Manager (OS-adaptive label)
        val revealLabel = when {
            SystemInfo.isMac     -> "Reveal in Finder"
            SystemInfo.isWindows -> "Reveal in Explorer"
            else                 -> "Open in File Manager"
        }
        menu.add(JMenuItem(revealLabel).apply {
            isEnabled = singleFile != null &&
                    java.awt.Desktop.isDesktopSupported() &&
                    java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)
            addActionListener {
                if (singleFile != null) {
                    AppExecutorUtil.getAppExecutorService().execute {
                        runCatching {
                            java.awt.Desktop.getDesktop().browseFileDirectory(java.io.File(singleFile.path))
                        }.onFailure { LOG.warn("Failed to reveal file in file manager: ${it.message}") }
                    }
                }
            }
        })

        // Open Terminal Here
        menu.add(JMenuItem("Open Terminal Here").apply {
            isEnabled = singleFile != null || contextDir != null
            addActionListener {
                val dir = if (singleFile?.isDirectory == true) singleFile.path
                          else singleFile?.parent?.path ?: contextDir?.path ?: return@addActionListener
                try {
                    TerminalToolWindowManager.getInstance(project).createLocalShellWidget(
                        dir, "Terminal: ${java.io.File(dir).name}", true, true
                    )
                } catch (e: Exception) {
                    LOG.warn("Failed to open terminal: ${e.message}")
                }
            }
        })

        menu.addSeparator()

        // --- Group 2: Clipboard actions ---

        // Copy
        menu.add(JMenuItem("Copy").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener {
                FileActions.copyToClipboard(selected)
                cutFiles = null
            }
        })

        // Cut
        menu.add(JMenuItem("Cut").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener {
                FileActions.copyToClipboard(selected)
                cutFiles = selected.toList()
            }
        })

        // Paste
        menu.add(JMenuItem("Paste").apply {
            isEnabled = contextDir != null
            addActionListener {
                if (contextDir != null) pasteFiles(contextDir)
            }
        })

        // Copy Path
        menu.add(JMenuItem("Copy Path").apply {
            isEnabled = singleFile != null
            addActionListener {
                if (singleFile != null) FileActions.copyPathToClipboard(singleFile)
            }
        })

        menu.addSeparator()

        // --- Group 3: Rename / Delete ---

        // Rename
        menu.add(JMenuItem("Rename").apply {
            isEnabled = singleFile != null
            addActionListener {
                if (singleFile != null) renameFile(singleFile)
            }
        })

        // Delete
        menu.add(JMenuItem("Delete").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener { deleteFiles(selected) }
        })

        menu.addSeparator()

        // --- Group 4: Create ---

        // New File
        menu.add(JMenuItem("New File").apply {
            isEnabled = contextDir != null
            addActionListener {
                if (contextDir != null) createNewFile(contextDir)
            }
        })

        // New Folder
        menu.add(JMenuItem("New Folder").apply {
            isEnabled = contextDir != null
            addActionListener {
                if (contextDir != null) createNewFolder(contextDir)
            }
        })

        menu.addSeparator()

        // --- Group 5: Bookmarks / Refresh ---

        // Add to Bookmarks (enabled for directories only)
        menu.add(JMenuItem("Add to Bookmarks").apply {
            isEnabled = singleFile?.isDirectory == true
            addActionListener {
                if (singleFile != null) addToBookmarks(singleFile)
            }
        })

        // Refresh (always enabled)
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

    private fun openEntry(file: VirtualFile) {
        if (file.isDirectory) {
            onDirectoryDoubleClicked?.invoke(file)
            return
        }
        NonProjectFileWritingAccessProvider.allowWriting(listOf(file))
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /**
     * Adds [children] to [parentNode] as tree nodes (pure model work, no VFS I/O).
     * Directories get a "loading..." placeholder child so the expander arrow appears.
     * Must be called on the EDT.
     */
    private fun populateNode(parentNode: DefaultMutableTreeNode, children: List<VirtualFile>) {
        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            if (child.isDirectory) {
                childNode.add(DefaultMutableTreeNode("loading..."))
            } else {
                childNode.allowsChildren = false
            }
            parentNode.add(childNode)
        }
    }

    /**
     * Returns the cached VCS color for a file or directory, or null if not yet computed.
     *
     * On cache miss, dispatches [computeVcsColor] on a background thread via
     * [ReadAction.nonBlocking]. The background result is stored in [vcsColorCache] and
     * a repaint is scheduled. This ensures FileStatusManager is never called on the EDT,
     * avoiding SlowOperations violations (IntelliJ 2025.1+).
     */
    private fun getEffectiveVcsColor(vf: VirtualFile): Color? {
        val cached = vcsColorCache[vf.path]
        if (cached != null) return cached.orElse(null)

        // Cache miss: dispatch off-EDT lookup if not already in flight
        if (vcsColorPending.add(vf.path)) {
            ReadAction.nonBlocking<java.util.Optional<Color>> {
                try { java.util.Optional.ofNullable(computeVcsColor(vf)) }
                catch (_: Exception) { java.util.Optional.empty() }
            }.submit(AppExecutorUtil.getAppExecutorService())
                .onSuccess { result ->
                    vcsColorPending.remove(vf.path)
                    vcsColorCache[vf.path] = result
                    ApplicationManager.getApplication().invokeLater {
                        if (!isDisposed) tree.repaint()
                    }
                }
                .onError { _ -> vcsColorPending.remove(vf.path) }
        }
        return null
    }

    /**
     * Computes the VCS color for [vf]. May call [FileStatusManager] — must only be
     * called from a background thread (never on the EDT).
     *
     * For files: returns the FileStatus color, or null if NOT_CHANGED.
     * For directories: scans 1-level of children and returns the first non-unchanged color.
     */
    private fun computeVcsColor(vf: VirtualFile): Color? {
        return if (vf.isDirectory) {
            val children = vf.children ?: return null
            children.firstNotNullOfOrNull { child ->
                val status = FileStatusManager.getInstance(project).getStatus(child)
                if (status != FileStatus.NOT_CHANGED) status.color else null
            }
        } else {
            val status = FileStatusManager.getInstance(project).getStatus(vf)
            if (status == FileStatus.NOT_CHANGED) null else status.color
        }
    }

    /**
     * Removes all listeners registered on the tree to prevent memory leaks.
     */
    override fun dispose() {
        isDisposed = true
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
                // Paint drop-hover highlight on directory rows during drag (DND-02)
                val hoveredRow = tree.getClientProperty("dnd.hoveredRow") as? Int ?: -1
                if (!selected && row == hoveredRow && vf.isDirectory) {
                    background = RenderingUtil.getSelectionBackground(tree)
                    isOpaque = true
                }

                icon = getIconForFile(vf)

                val vcsColor = getEffectiveVcsColor(vf)
                val textStyle = if (vcsColor != null)
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, vcsColor)
                else
                    SimpleTextAttributes.REGULAR_ATTRIBUTES

                // Add file permissions if enabled (right-aligned)
                // Use cached flag to avoid expensive getInstance() calls on every cell render
                if (showPermissions) {
                    val perms = getCachedPermissions(vf)
                    if (perms.isNotEmpty()) {
                        val treeWidth = tree.visibleRect.width
                        val permissionsWidth = 120 // Reserve space for permissions (drwxr-xr-x = ~100px)
                        val margin = 20 // Extra margin for safety
                        val availableWidth = treeWidth - permissionsWidth - margin

                        // Manually truncate filename if needed
                        val fm = getFontMetrics(font)
                        val truncatedName = truncateString(vf.name, fm, availableWidth)

                        // Append truncated filename with VCS color
                        append(truncatedName, textStyle)

                        // Add padding to push permissions to the right
                        appendTextPadding(treeWidth - permissionsWidth)

                        // Append permissions
                        append(perms, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    } else {
                        // No permissions to show, just append the filename with VCS color
                        append(vf.name, textStyle)
                    }
                } else {
                    // Permissions not enabled, just append the filename with VCS color
                    append(vf.name, textStyle)
                    if (showFolderItemCount && vf.isDirectory) {
                        val count = vf.children.size
                        if (count > 0) append(" ($count)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    }
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
            return iconCache.getOrPut(vf.path) {
                if (vf.isDirectory) AllIcons.Nodes.Folder
                else FileTypeManager.getInstance().getFileTypeByFileName(vf.name).icon
                    ?: AllIcons.FileTypes.Any_type
            }
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

        private fun formatPermissions(file: VirtualFile): String =
            formatPermissionsForFile(file)
    }

    private fun formatPermissionsForFile(file: VirtualFile): String {
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
