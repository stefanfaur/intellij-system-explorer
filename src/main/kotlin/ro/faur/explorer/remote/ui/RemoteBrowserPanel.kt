package ro.faur.explorer.remote.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.CrossPanelTransferService
import ro.faur.explorer.remote.DirectoryCache
import ro.faur.explorer.remote.RemoteBookmark
import ro.faur.explorer.remote.RemoteBookmarkManager
import ro.faur.explorer.remote.RemoteEditorManager
import ro.faur.explorer.remote.RemoteFileClipboardData
import ro.faur.explorer.remote.RemoteFileTransferable
import ro.faur.explorer.remote.RemotePathUtils
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.SftpEntry
import ro.faur.explorer.remote.SftpFileOperations
import ro.faur.explorer.remote.SftpFileTreeModel
import ro.faur.explorer.remote.SshTerminalAction
import ro.faur.explorer.remote.security.SecureTempFileManager
import ro.faur.explorer.remote.git.ActiveConnectionInfo
import ro.faur.explorer.remote.git.ActiveConnectionRegistry
import ro.faur.explorer.remote.git.RemoteGitCommandExecutor
import ro.faur.explorer.remote.git.RemoteGitRootDetector
import ro.faur.explorer.remote.git.RemoteGitSettings
import ro.faur.explorer.remote.git.RemoteGitStatusCache
import ro.faur.explorer.remote.git.RemoteGitTreeDecorator
import ro.faur.explorer.remote.git.RemoteGitVcs
import ro.faur.explorer.remote.git.RemoteGitVcsManager
import ro.faur.explorer.gitpanel.ActiveBrowserTracker
import ro.faur.explorer.gitpanel.GitRepositoryRegistry
import ro.faur.explorer.gitpanel.RemoteGitBackend
import ro.faur.explorer.ui.BrowserPanel
import java.awt.Color
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.SwingUtilities
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Browser panel backed by an SFTP connection.
 * Extends [BrowserPanel] so it shares toolbar, path bar, filter, status bar,
 * navigation history, and toggle checkboxes with [ro.faur.explorer.ui.LocalBrowserPanel].
 */
class RemoteBrowserPanel(
    private val project: Project,
    private val transferService: CrossPanelTransferService? = null,
    val connectionManager: SftpConnectionManager? = null,
) : BrowserPanel() {

    private val directoryCache = DirectoryCache(ttl = Duration.ofMinutes(2), maxEntries = 200)
    private val sftpFileTreeModel = SftpFileTreeModel(directoryCache)
    private val tree = Tree(sftpFileTreeModel.treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = RemoteFileCellRenderer()
    }

    private var _currentPath: String = "/"
    private var homeDirectory: String = "/"
    private var connectionName: String? = null
    private var connectionProfile: ConnectionProfile? = null
    private var fileOps: SftpFileOperations? = null
    private var filterPattern: String = ""

    private val gitRootDetector = RemoteGitRootDetector.forProject(project)
    @Volatile private var gitAvailable: Boolean? = null
    private var activeDropTarget: RemoteTreeDropTarget? = null
    private val statusPollExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r).also { it.isDaemon = true; it.name = "RemoteGitStatusPoll" }
    }
    private var statusPollFuture: ScheduledFuture<*>? = null

    val isConnected: Boolean get() = fileOps != null

    override val panelLabel: String get() = connectionProfile?.name ?: connectionName ?: "Remote"
    override val panelIcon: Icon = AllIcons.Nodes.DataTables

    private val editorManager = RemoteEditorManager(project, SecureTempFileManager())

    init {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_BACK_SPACE -> navigateBack()
                    e.keyCode == KeyEvent.VK_ENTER -> {
                        val entries = getSelectedEntries()
                        if (entries.size == 1) openEntry(entries.first())
                    }
                    e.keyCode == KeyEvent.VK_C && (e.modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0 ->
                        copySelectedToClipboard()
                }
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val node = getNodeAt(e) ?: return
                    val entry = node.userObject as? SftpEntry ?: return
                    openEntry(entry)
                }
            }
            override fun mousePressed(e: MouseEvent)  { if (e.isPopupTrigger) showContextMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showContextMenu(e) }
        })

        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node  = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val entry = node.userObject as? SftpEntry ?: return
                if (!entry.isDirectory) return
                // Only trigger if still showing the loading placeholder
                val isPlaceholder = node.childCount == 1 &&
                    (node.firstChild as? DefaultMutableTreeNode)?.userObject == SftpFileTreeModel.LOADING_PLACEHOLDER
                if (!isPlaceholder) return

                val ops     = fileOps ?: return
                val connKey = connectionName ?: return
                sftpFileTreeModel.loadChildren(
                    connKey    = connKey,
                    parentNode = node,
                    path       = entry.path,
                    fileOps    = ops,
                    filter     = { entries ->
                        val visible = applyFilters(entries)
                        visible
                    }
                )
            }
            override fun treeWillCollapse(event: TreeExpansionEvent) {}
        })

        tree.transferHandler = RemoteTreeTransferHandler(this)
        tree.dragEnabled = true

        assemblePanelUI(JBScrollPane(tree))

        wireSharedListeners()

        wireToggleListeners(
            onHiddenChanged      = { doNavigateTo(_currentPath) },
            onPermissionsChanged = { tree.repaint() }
        )

        filterField.addActionListener {
            filterPattern = filterField.text.trim()
            doNavigateTo(_currentPath)
        }
    }

    // ── BrowserPanel contract ──────────────────────────────────────────────

    override fun doNavigateTo(path: String) {
        val ops = fileOps ?: return
        val connKey = connectionName ?: return
        _currentPath   = path
        pathField.text = path
        ActiveBrowserTracker.getInstance(project).reportNavigation(connKey, path)

        sftpFileTreeModel.loadDirectory(
            connKey  = connKey,
            path     = path,
            fileOps  = ops,
            filter   = { entries ->
                val visible = applyFilters(entries)
                updateStatusFromEntries(visible)
                visible
            },
            onLoaded = { entries -> onDirectoryLoaded(connKey, path, entries) }
        )
    }

    override fun navigateUp() {
        RemotePathUtils.parentPath(_currentPath).let { if (it != _currentPath) navigateTo(it) }
    }

    override fun navigateHome() {
        navigateTo(homeDirectory)
    }

    override fun refresh() {
        val connKey = connectionName ?: return
        directoryCache.invalidate(connKey, _currentPath)
        doNavigateTo(_currentPath)
    }

    override fun currentPath(): String = _currentPath

    override fun getSelectedPaths(): List<String> =
        getSelectedEntries().map { it.path }

    override fun onPathEntered(path: String) {
        navigateTo(path)
    }

    // ── Connection lifecycle ───────────────────────────────────────────────

    fun connect(name: String, ops: SftpFileOperations, profile: ConnectionProfile? = null) {
        connectionName    = name
        fileOps           = ops
        connectionProfile = profile
        gitAvailable      = null
        homeDirectory     = "/"

        activeDropTarget?.dispose()
        val dropTarget = RemoteTreeDropTarget(this, transferService, name)
        activeDropTarget = dropTarget
        DnDManager.getInstance().registerTarget(dropTarget, tree)

        notifyNavStateChanged()

        // Resolve the home directory via SFTP realpath(".") on a background thread,
        // then begin navigation. Avoids a momentary flash to "/" before redirecting.
        Thread {
            val home = ops.homeDir()
            ApplicationManager.getApplication().invokeLater {
                if (fileOps !== ops) return@invokeLater   // disconnected before resolution
                homeDirectory = home
                history.push(home)
                doNavigateTo(home)
                notifyNavStateChanged()
            }
        }.also { it.isDaemon = true; it.name = "RemoteHomeDir[$name]" }.start()
    }

    fun disconnect() {
        stopGitStatusPolling()
        activeDropTarget?.let { DnDManager.getInstance().unregisterTarget(it, tree) }
        activeDropTarget?.dispose()
        activeDropTarget = null
        val oldName = connectionName
        connectionName    = null
        connectionProfile = null
        fileOps           = null
        gitAvailable      = null
        homeDirectory     = "/"
        if (oldName != null) {
            GitRepositoryRegistry.getInstance(project).unregisterByConnection(oldName)
            directoryCache.invalidateAll(oldName)
            ActiveConnectionRegistry.clear(oldName)
            RemoteGitVcsManager.getInstance(project).clearConnection(oldName)
            RemoteGitStatusCache.invalidate(oldName)
        }
        editorManager.cleanupTempFiles()
        sftpFileTreeModel.showDisconnected()
        pathField.text = ""
        statusLabel.text = "Disconnected"
    }

    fun getConnectionName(): String? = connectionName

    fun getCurrentPath(): String = _currentPath

    // ── Filtering ──────────────────────────────────────────────────────────

    private fun applyFilters(entries: List<SftpEntry>): List<SftpEntry> {
        var result = if (!showHidden) entries.filter { !it.name.startsWith(".") } else entries
        if (filterPattern.isNotEmpty()) {
            val regex = filterPattern.replace("*", ".*").replace("?", ".").toRegex(RegexOption.IGNORE_CASE)
            result = result.filter { it.isDirectory || regex.matches(it.name) }
        }
        return result
    }

    // ── Status bar ─────────────────────────────────────────────────────────

    private fun updateStatusFromEntries(entries: List<SftpEntry>) {
        ApplicationManager.getApplication().invokeLater {
            val folders = entries.count { it.isDirectory }
            val files   = entries.count { !it.isDirectory }
            statusLabel.text = "$folders folders, $files files"
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun getSelectedEntries(): List<SftpEntry> =
        tree.selectionPaths?.mapNotNull { path ->
            (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? SftpEntry
        } ?: emptyList()

    // ── Git integration ────────────────────────────────────────────────────

    private fun onDirectoryLoaded(connKey: String, path: String, entries: List<SftpEntry>) {
        if (gitAvailable == false) return
        val wasKnownRoot = RemoteGitVcsManager.getInstance(project).isInsideRepo(connKey, path)
        gitRootDetector.detectAndRegister(connKey, path, entries)
        val isNowRoot = RemoteGitVcsManager.getInstance(project).getRoots(connKey).contains(path)
        if (isNowRoot && !wasKnownRoot) {
            if (gitAvailable == null) gitAvailable = checkGitAvailable(connKey)
            if (gitAvailable == true && connectionManager != null) {
                val vcs = RemoteGitVcs.getInstance(project) ?: return
                vcs.configure(connectionManager, connKey, path)
                ActiveConnectionRegistry.set(ActiveConnectionInfo(connectionManager, connKey, path))
                startGitStatusPolling(connKey)
                val panelExecutor = RemoteGitCommandExecutor(connectionManager!!, connKey)
                val backend = RemoteGitBackend(connKey, path, panelExecutor)
                GitRepositoryRegistry.getInstance(project).register(backend)
            }
        }
    }

    private fun checkGitAvailable(connKey: String): Boolean {
        val cm = connectionManager ?: return false
        return try {
            RemoteGitCommandExecutor(cm, connKey)
                .executeBlocking("/", timeout = Duration.ofSeconds(5), args = arrayOf("--version"))
                .isSuccess
        } catch (_: Exception) { false }
    }

    private fun startGitStatusPolling(connKey: String) {
        if (statusPollFuture != null) return
        val cm = connectionManager ?: return
        val intervalSec = RemoteGitSettings.getInstance().state.statusRefreshIntervalSec.toLong()
        statusPollFuture = statusPollExecutor.scheduleWithFixedDelay({
            try {
                val roots = RemoteGitVcsManager.getInstance(project).getRoots(connKey)
                val exec  = RemoteGitCommandExecutor(cm, connKey)
                for (root in roots) {
                    val result = exec.executeBlocking(root, Duration.ofSeconds(15),
                        args = arrayOf("status", "--porcelain=v2", "--untracked-files=all"))
                    if (result.isSuccess) RemoteGitStatusCache.update(connKey, root, result.stdoutLines)
                }
                ApplicationManager.getApplication().invokeLater { tree.repaint() }
            } catch (_: Exception) {}
        }, intervalSec, intervalSec, TimeUnit.SECONDS)
    }

    private fun stopGitStatusPolling() {
        statusPollFuture?.cancel(true)
        statusPollFuture = null
    }

    // ── Context menu ───────────────────────────────────────────────────────

    private fun showContextMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y)
        if (path != null && !tree.isPathSelected(path)) tree.selectionPath = path

        val selected = getSelectedEntries()
        val ops      = fileOps ?: return
        val connKey  = connectionName ?: return
        val menu     = JPopupMenu()

        val openItem = JMenuItem("Open").apply {
            isEnabled = selected.size == 1
            addActionListener {
                val entry = selected.firstOrNull() ?: return@addActionListener
                openEntry(entry)
            }
        }
        menu.add(openItem)
        menu.add(JSeparator())

        val copyPathItem = JMenuItem("Copy Path").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener {
                val paths = selected.joinToString("\n") { it.path }
                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(paths), null)
            }
        }
        menu.add(copyPathItem)
        menu.add(JSeparator())

        val renameItem = JMenuItem("Rename").apply {
            isEnabled = selected.size == 1
            addActionListener {
                val entry   = selected.firstOrNull() ?: return@addActionListener
                val newName = JOptionPane.showInputDialog(this@RemoteBrowserPanel, "New name:", entry.name)
                    ?: return@addActionListener
                if (newName.isBlank() || newName == entry.name) return@addActionListener
                Thread {
                    try {
                        ops.rename(entry.path, RemotePathUtils.join(RemotePathUtils.parentPath(entry.path), newName))
                        directoryCache.invalidate(connKey, _currentPath)
                        ApplicationManager.getApplication().invokeLater { doNavigateTo(_currentPath) }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            JOptionPane.showMessageDialog(this@RemoteBrowserPanel, "Rename failed: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }.also { it.isDaemon = true }.start()
            }
        }
        menu.add(renameItem)

        val deleteItem = JMenuItem("Delete").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener {
                if (JOptionPane.showConfirmDialog(this@RemoteBrowserPanel, "Delete ${selected.size} item(s)?",
                        "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return@addActionListener
                Thread {
                    var hasErrors = false
                    for (entry in selected) try {
                        if (entry.isDirectory) ops.deleteDirectory(entry.path) else ops.deleteFile(entry.path)
                    } catch (_: Exception) { hasErrors = true }
                    directoryCache.invalidate(connKey, _currentPath)
                    ApplicationManager.getApplication().invokeLater {
                        doNavigateTo(_currentPath)
                        if (hasErrors) JOptionPane.showMessageDialog(this@RemoteBrowserPanel, "Some items could not be deleted.", "Error", JOptionPane.WARNING_MESSAGE)
                    }
                }.also { it.isDaemon = true }.start()
            }
        }
        menu.add(deleteItem)
        menu.add(JSeparator())

        val newFileItem = JMenuItem("New File").apply {
            addActionListener {
                val name = JOptionPane.showInputDialog(this@RemoteBrowserPanel, "File name:") ?: return@addActionListener
                if (name.isBlank()) return@addActionListener
                Thread {
                    try {
                        val tempFile = Files.createTempFile("remote-new", null)
                        ops.upload(tempFile, RemotePathUtils.join(_currentPath, name))
                        Files.deleteIfExists(tempFile)
                        directoryCache.invalidate(connKey, _currentPath)
                        ApplicationManager.getApplication().invokeLater { doNavigateTo(_currentPath) }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            JOptionPane.showMessageDialog(this@RemoteBrowserPanel, "Failed to create file: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }.also { it.isDaemon = true }.start()
            }
        }
        menu.add(newFileItem)

        val newFolderItem = JMenuItem("New Folder").apply {
            addActionListener {
                val name = JOptionPane.showInputDialog(this@RemoteBrowserPanel, "Folder name:") ?: return@addActionListener
                if (name.isBlank()) return@addActionListener
                Thread {
                    try {
                        ops.mkdir(RemotePathUtils.join(_currentPath, name))
                        directoryCache.invalidate(connKey, _currentPath)
                        ApplicationManager.getApplication().invokeLater { doNavigateTo(_currentPath) }
                    } catch (ex: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            JOptionPane.showMessageDialog(this@RemoteBrowserPanel, "Failed to create folder: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }.also { it.isDaemon = true }.start()
            }
        }
        menu.add(newFolderItem)
        menu.add(JSeparator())

        val bookmarkItem = JMenuItem("Add to Bookmarks").apply {
            isEnabled = selected.isNotEmpty()
            addActionListener {
                val bmgr = RemoteBookmarkManager.getInstance(project)
                for (entry in selected) {
                    val label = JOptionPane.showInputDialog(this@RemoteBrowserPanel,
                        "Bookmark label for '${entry.name}':", entry.name) ?: continue
                    bmgr.addBookmark(connKey, RemoteBookmark(path = entry.path, label = label))
                }
            }
        }
        menu.add(bookmarkItem)
        menu.add(JSeparator())

        val terminalItem = JMenuItem("Open in Terminal").apply {
            isEnabled = connectionProfile != null
            addActionListener {
                val profile = connectionProfile ?: return@addActionListener
                val targetDir = if (selected.size == 1 && selected.first().isDirectory) selected.first().path else _currentPath
                SshTerminalAction.openTerminal(project, profile, targetDir)
            }
        }
        menu.add(terminalItem)

        val compareItem = JMenuItem("Compare with Remote").apply {
            isEnabled = selected.size == 1 && selected.firstOrNull()?.isDirectory == false
            addActionListener {
                val entry = selected.firstOrNull() ?: return@addActionListener
                compareWithRemote(entry, ops)
            }
        }
        menu.add(compareItem)
        menu.add(JSeparator())

        val refreshItem = JMenuItem("Refresh").apply { addActionListener { refresh() } }
        menu.add(refreshItem)

        menu.show(tree, e.x, e.y)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun openEntry(entry: SftpEntry) {
        if (entry.isDirectory) { navigateTo(entry.path); return }
        val ops  = fileOps ?: return
        val host = connectionProfile?.host ?: return
        val conn = connectionName ?: return
        editorManager.openRemoteFile(conn, host, entry.path, ops)
    }

    private fun getNodeAt(e: MouseEvent): DefaultMutableTreeNode? {
        val treePath = tree.getPathForLocation(e.x, e.y) ?: return null
        return treePath.lastPathComponent as? DefaultMutableTreeNode
    }

    private fun copySelectedToClipboard() {
        val connKey  = connectionName ?: return
        val entries  = getSelectedEntries()
        if (entries.isEmpty()) return
        CopyPasteManager.getInstance().setContents(
            RemoteFileTransferable(RemoteFileClipboardData(connKey, entries))
        )
    }

    private fun compareWithRemote(entry: SftpEntry, ops: SftpFileOperations) {
        Thread {
            try {
                val freshTemp = Files.createTempFile("remote-compare-", "-${entry.name}")
                ops.download(entry.path, freshTemp)
                ApplicationManager.getApplication().invokeLater {
                    val freshVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(freshTemp) ?: return@invokeLater
                    val cf      = DiffContentFactory.getInstance()
                    DiffManager.getInstance().showDiff(project,
                        SimpleDiffRequest("Compare: ${entry.name}", cf.create(project, freshVf), cf.create(project, freshVf),
                            "Remote (${entry.path})", "Remote (fresh download)"))
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(this, "Compare failed: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.also { it.isDaemon = true; it.name = "RemoteCompare[${entry.path}]" }.start()
    }

    // ── Cell renderer ──────────────────────────────────────────────────────

    private inner class RemoteFileCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree, value: Any?, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            val node  = value as? DefaultMutableTreeNode ?: return
            val entry = node.userObject as? SftpEntry ?: run {
                val text = node.userObject?.toString() ?: ""
                if (text == SftpFileTreeModel.ACCESS_DENIED_PLACEHOLDER) {
                    setIcon(AllIcons.General.Warning)
                    append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                } else {
                    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                return
            }
            val connKey   = connectionName
            val gitStatus = connKey?.let { RemoteGitStatusCache.getStatus(it, entry.path) }

            val mainAttrs: SimpleTextAttributes = when {
                entry.isBrokenSymlink -> SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, Color(128, 128, 128))
                gitStatus != null -> RemoteGitTreeDecorator.colorFor(gitStatus)
                    ?.let { SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, it) }
                    ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
                else -> SimpleTextAttributes.REGULAR_ATTRIBUTES
            }

            setIcon(when { entry.isDirectory -> AllIcons.Nodes.Folder; entry.isSymlink -> AllIcons.Nodes.Symlink; else -> AllIcons.FileTypes.Any_type })
            append(if (entry.isSymlink) "${entry.name} →" else entry.name, mainAttrs)
            if (!entry.isDirectory) append("  ${formatSize(entry.size)}", SimpleTextAttributes.GRAY_ATTRIBUTES)
            if (showPermissions && entry.permissions != null) {
                val treeWidth = tree.visibleRect.width
                val permissionsWidth = 120
                appendTextPadding(treeWidth - permissionsWidth)
                append(entry.permissions!!, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            if (entry.isSymlink && !entry.isBrokenSymlink) toolTipText = "Symlink: ${entry.name}"
            else if (entry.isBrokenSymlink) toolTipText = "Broken symlink: ${entry.name}"
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024             -> "${bytes}B"
            bytes < 1024 * 1024      -> "${bytes / 1024}KB"
            bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else                     -> "${bytes / (1024L * 1024 * 1024)}GB"
        }
    }

    // ── Dispose ────────────────────────────────────────────────────────────

    override fun dispose() {
        super.dispose()
        stopGitStatusPolling()
        statusPollExecutor.shutdownNow()
        editorManager.cleanupTempFiles()
        fileOps = null
    }
}
