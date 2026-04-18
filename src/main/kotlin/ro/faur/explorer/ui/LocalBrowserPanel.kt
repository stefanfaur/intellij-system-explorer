package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.gitpanel.ActiveBrowserTracker
import ro.faur.explorer.gitpanel.BackendType
import ro.faur.explorer.gitpanel.GitRepositoryRegistry
import ro.faur.explorer.gitpanel.LocalGitBackend
import ro.faur.explorer.model.BookmarkManager
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.shortcuts.ChordShortcutHandler
import ro.faur.explorer.util.FileSizeFormatter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Browser panel backed by the local filesystem (IntelliJ VFS).
 *
 * Wraps [FileTreeComponent] and [BookmarksPanel].
 * This panel is always present as panel #0 in [BrowserHost].
 */
class LocalBrowserPanel(private val project: Project) : BrowserPanel(), ChordShortcutHandler {

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(LocalBrowserPanel::class.java)
    }

    override val panelLabel: String = "Local"
    override val panelIcon: Icon    = AllIcons.Nodes.HomeFolder

    @Volatile private var isDisposed = false

    internal val fileTreeComponent = FileTreeComponent(project)

    private val bookmarksPanel = BookmarksPanel(
        onBookmarkSelected = { path -> navigateTo(path) },
        onBookmarkMoved = { from, to ->
            try { BookmarkManager.getInstance().moveBookmark(from, to) }
            catch (e: Exception) { LOG.warn("Failed to move bookmark", e) }
        },
        onBookmarkDeleted = { index ->
            try {
                val mgr = BookmarkManager.getInstance()
                val bookmarks = mgr.getBookmarks()
                if (index in bookmarks.indices) {
                    mgr.removeBookmark(bookmarks[index].path)
                    loadBookmarks()
                }
            } catch (e: Exception) { LOG.warn("Failed to delete bookmark", e) }
        }
    )

    private var _currentPath: String = NavigationActions.goHome(project)
    private val presetComboBox = JComboBox<String>()

    override fun buildSharedNorth(): JPanel {
        val north = super.buildSharedNorth()
        refreshPresets()

        val filterRow = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, filterField.preferredSize.height)
        }
        // Move the last component (filterField) from the BoxLayout into filterRow
        north.remove(north.componentCount - 1)
        filterRow.add(filterField, BorderLayout.CENTER)
        presetComboBox.maximumSize = Dimension(120, filterField.preferredSize.height)
        filterRow.add(presetComboBox, BorderLayout.EAST)
        north.add(filterRow)

        presetComboBox.addActionListener {
            val selected = presetComboBox.selectedItem as? String ?: return@addActionListener
            val preset = try {
                ExplorerSettings.getInstance().state.globPresets.find { it.name == selected }
            } catch (_: Exception) { null }
            if (preset != null) {
                filterField.text = preset.pattern
                fileTreeComponent.filterPattern = preset.pattern
                fileTreeComponent.setRoot(_currentPath)
                updateStatus()
            }
        }
        return north
    }

    private fun refreshPresets() {
        val model = DefaultComboBoxModel<String>()
        model.addElement("Presets\u2026")
        try {
            ExplorerSettings.getInstance().state.globPresets.forEach { model.addElement(it.name) }
        } catch (_: Exception) {}
        presetComboBox.model = model
    }

    init {
        history.push(_currentPath)
        Disposer.register(this, fileTreeComponent)

        fileTreeComponent.onDirectoryDoubleClicked = { vf -> navigateTo(vf.path) }
        fileTreeComponent.onSelectionChanged       = { updateStatus() }
        fileTreeComponent.onFilesModified          = { updateStatus(); loadBookmarks() }
        fileTreeComponent.onRootLoaded             = { updateStatus() }

        val splitter = JBSplitter(false, 0.2f).apply {
            firstComponent  = bookmarksPanel.component
            secondComponent = JBScrollPane(fileTreeComponent.tree)
        }
        assemblePanelUI(splitter)

        // Restore persisted toggle state
        val settings = ExplorerSettings.getInstance()
        fileTreeComponent.showPermissions = settings.state.showFilePermissions
        fileTreeComponent.showFolderItemCount = settings.state.showFolderItemCount
        showPermissions                   = settings.state.showFilePermissions
        refreshToggleAppearance()

        wireSharedListeners()

        wireToggleListeners(
            onHiddenChanged = { hidden ->
                fileTreeComponent.showHidden = hidden
                fileTreeComponent.setRoot(_currentPath)
                updateStatus()
            },
            onPermissionsChanged = { perms ->
                ExplorerSettings.getInstance().state.showFilePermissions = perms
                fileTreeComponent.showPermissions = perms
                fileTreeComponent.tree.repaint()
            }
        )

        filterField.addActionListener {
            fileTreeComponent.filterPattern = filterField.text.trim()
            fileTreeComponent.setRoot(_currentPath)
            updateStatus()
        }

        pathField.text = _currentPath
        fileTreeComponent.setRoot(_currentPath)
        loadBookmarks()
        updateStatus()
        notifyNavStateChanged()
    }

    // ── Abstract contract ──────────────────────────────────────────────────

    override fun navigateTo(path: String) {
        if (path == _currentPath) return   // already here — skip redundant history push
        super.navigateTo(path)
    }

    override fun doNavigateTo(path: String) {
        _currentPath   = path
        pathField.text = path
        fileTreeComponent.setRoot(path)
        bookmarksPanel.highlightForPath(path)
        try { FrecencyStore.getInstance().recordVisit(path) } catch (_: Exception) {}
        updateStatus()
        autoDetectLocalGitRepo(path)
        ActiveBrowserTracker.getInstance(project).reportNavigation(null, path)
    }

    private fun autoDetectLocalGitRepo(startPath: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            var dir = java.io.File(startPath)
            while (true) {
                if (java.io.File(dir, ".git").isDirectory) {
                    val repoPath = dir.absolutePath
                    val registry = GitRepositoryRegistry.getInstance(project)
                    val alreadyRegistered = registry.getAll().any {
                        it.id.type == BackendType.LOCAL && it.repoPath == repoPath
                    }
                    if (!alreadyRegistered) {
                        ApplicationManager.getApplication().invokeLater {
                            registry.register(LocalGitBackend(repoPath))
                        }
                    }
                    break
                }
                val parent = dir.parentFile ?: break
                if (parent == dir) break
                dir = parent
            }
        }
    }

    override fun navigateUp() {
        NavigationActions.goToParent(_currentPath).let { if (it != _currentPath) navigateTo(it) }
    }

    override fun navigateHome() {
        navigateTo(NavigationActions.goHome(project))
    }

    override fun refresh() {
        fileTreeComponent.setRoot(_currentPath)
        updateStatus()
    }

    override fun currentPath(): String = _currentPath

    override fun getSelectedPaths(): List<String> =
        fileTreeComponent.getSelectedFiles().map { it.path }

    // ── Local-specific public API ──────────────────────────────────────────

    fun focusFileTree() {
        val tree = fileTreeComponent.tree
        if (tree.selectionCount == 0 && tree.rowCount > 0) tree.setSelectionRow(0)
        IdeFocusManager.getInstance(project).requestFocus(tree, true)
    }

    fun toggleHiddenFiles() {
        hiddenToggle.doClick()
    }

    fun getStatusText(): String = statusLabel.text

    // ── Chord shortcut handlers ─────────────────────────────────────────
    
    override fun triggerNewFile() {
        com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "Enter file name:",
            "New File",
            null
        )?.let { name ->
            val parentPath = _currentPath
            val newFile = java.io.File(parentPath, name)
            if (!newFile.exists()) {
                newFile.createNewFile()
                refresh()
            }
        }
    }
    
    override fun triggerNewFolder() {
        com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "Enter folder name:",
            "New Folder",
            null
        )?.let { name ->
            val parentPath = _currentPath
            val newFolder = java.io.File(parentPath, name)
            if (!newFolder.exists()) {
                newFolder.mkdir()
                refresh()
            }
        }
    }
    
    override fun triggerEditInIde() {
        val selected = fileTreeComponent.getSelectedFiles().firstOrNull() ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(selected.path) ?: return
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
    
    override fun triggerShowInTerminal() {
        try {
            val terminalManager = org.jetbrains.plugins.terminal.TerminalToolWindowManager.getInstance(project)
            terminalManager.createLocalShellWidget(null, "System Explorer", true, true)
                .executeCommand("cd \"${currentPath()}\"")
        } catch (_: Exception) {
            // Terminal plugin not available
        }
    }
    
    override fun triggerShowInExplorer() {
        val selected = fileTreeComponent.getSelectedFiles().firstOrNull() ?: return
        val path = selected.path
        // Reveal in Finder on macOS, Explorer on Windows, file manager on Linux
        when {
            System.getProperty("os.name").lowercase().contains("mac") -> {
                Runtime.getRuntime().exec(arrayOf("open", "-R", path))
            }
            System.getProperty("os.name").lowercase().contains("win") -> {
                Runtime.getRuntime().exec(arrayOf("explorer", "/select,", path))
            }
            else -> {
                // Linux: try common file managers
                val managers = listOf("nautilus", "dolphin", "thunar", "pcmanfm")
                for (cmd in managers) {
                    try {
                        Runtime.getRuntime().exec(arrayOf(cmd, path))
                        break
                    } catch (_: Exception) {}
                }
            }
        }
    }
    
    override fun triggerCopyName() {
        val selected = fileTreeComponent.getSelectedFiles().firstOrNull() ?: return
        val name = selected.name
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val contents = java.awt.datatransfer.StringSelection(name)
        clipboard.setContents(contents, contents)
    }

    // ── Overridden path-field handler (validates VFS path) ─────────────────

    override fun onPathEntered(path: String) {
        val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        if (vf.isDirectory) navigateTo(path)
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun loadBookmarks() {
        try {
            val mgr = BookmarkManager.getInstance()
            bookmarksPanel.setBookmarks(mgr.getBookmarks())
            bookmarksPanel.highlightForPath(_currentPath)
        } catch (e: Exception) { LOG.warn("Failed to load bookmarks", e) }
    }

    internal fun updateStatus() {
        val detail = try { ExplorerSettings.getInstance().state.statusBarDetail } catch (_: Exception) { "normal" }
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isNotEmpty()) {
            if (detail == "minimal") {
                statusLabel.text = "${selected.size} item(s)"
                return
            }
            AppExecutorUtil.getAppExecutorService().execute {
                val statusText = ReadAction.compute<String, Throwable> {
                    when (detail) {
                        "verbose" -> {
                            val dirs  = selected.filter { it.isDirectory }
                            val files = selected.filter { !it.isDirectory }
                            val fileSizeBytes = files.sumOf { it.length }
                            val dirChildCount = dirs.sumOf { FileSizeFormatter.countDirectChildren(it) }
                            val permsStr = selected.firstOrNull()?.let {
                                runCatching { java.nio.file.Files.getPosixFilePermissions(java.nio.file.Paths.get(it.path)).toString() }.getOrDefault("")
                            } ?: ""
                            "${selected.size} selected -- $dirChildCount items, ${FileSizeFormatter.format(fileSizeBytes)}, perms: $permsStr"
                        }
                        else -> {
                            val dirs  = selected.filter { it.isDirectory }
                            val files = selected.filter { !it.isDirectory }
                            val fileSizeBytes  = files.sumOf { it.length }
                            val dirChildCount  = dirs.sumOf { FileSizeFormatter.countDirectChildren(it) }
                            when {
                                dirs.isEmpty()  -> "${selected.size} selected -- ${FileSizeFormatter.format(fileSizeBytes)}"
                                files.isEmpty() -> {
                                    val dirSizeBytes = dirs.sumOf { FileSizeFormatter.computeDirectoryImmediateSize(it) }
                                    "${selected.size} selected -- $dirChildCount items, ${FileSizeFormatter.format(dirSizeBytes)}"
                                }
                                else -> if (dirChildCount > 0)
                                    "${selected.size} selected -- $dirChildCount items in dirs, ${FileSizeFormatter.format(fileSizeBytes)} in files"
                                else
                                    "${selected.size} selected -- ${FileSizeFormatter.format(fileSizeBytes)}"
                            }
                        }
                    }
                }
                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) statusLabel.text = statusText
                }
            }
        } else {
            val children    = fileTreeComponent.getRootChildren()
            val folderCount = children.count { it.isDirectory }
            val fileCount   = children.count { !it.isDirectory }
            statusLabel.text = "$folderCount folders, $fileCount files"
        }
    }

    override fun dispose() {
        isDisposed = true
        super.dispose()
        // FileTreeComponent is registered as child disposable via Disposer.register above
    }
}
