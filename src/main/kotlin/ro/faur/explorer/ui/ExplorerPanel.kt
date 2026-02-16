package ro.faur.explorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.util.FileSizeFormatter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The main panel for the System Explorer tool window.
 *
 * Contains:
 * - A toolbar at the top with navigation buttons (Back, Forward, Up, Home, Refresh, Settings) and an editable path bar
 * - A filter text field for glob pattern filtering
 * - A splitter with bookmarks sidebar on the left and a file tree on the right
 * - A status bar at the bottom with file counts on the left and toggle checkboxes on the right
 *
 * Properties:
 * - [currentPath]: The currently displayed directory path
 * - [component]: The root Swing component to embed in the tool window
 *
 * Methods:
 * - [navigateTo]: Changes the current directory to the given path
 *
 * Implements [Disposable] so that all registered listeners are cleaned up when the panel
 * is disposed, preventing memory leaks.
 */
class ExplorerPanel(private val project: Project) : Disposable {

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ExplorerPanel::class.java)
    }

    val component: JComponent

    var currentPath: String
        private set

    private val history = NavigationActions.NavigationHistory()

    /**
     * Returns the navigation history for use by the Quick Open dialog.
     */
    fun getNavigationHistory(): NavigationActions.NavigationHistory = history
    internal val fileTreeComponent = FileTreeComponent(project)
    private val bookmarksPanel = BookmarksPanel(
        onBookmarkSelected = { path -> navigateTo(path) },
        onBookmarkMoved = { from, to ->
            try {
                ro.faur.explorer.model.BookmarkManager.getInstance().moveBookmark(from, to)
            } catch (e: Exception) {
                LOG.warn("Failed to move bookmark", e)
            }
        },
        onBookmarkDeleted = { index ->
            try {
                val manager = ro.faur.explorer.model.BookmarkManager.getInstance()
                val bookmarks = manager.getBookmarks()
                if (index in bookmarks.indices) {
                    manager.removeBookmark(bookmarks[index].path)
                    loadBookmarks()
                }
            } catch (e: Exception) {
                LOG.warn("Failed to delete bookmark", e)
            }
        }
    )
    private val pathField = JBTextField()
    private val filterField = JBTextField()
    private val statusLabel = JBLabel("Ready")

    // Toolbar buttons
    private val backButton = JButton("<")
    private val forwardButton = JButton(">")
    private val upButton = JButton("Up")
    private val homeButton = JButton("Home")
    private val refreshButton = JButton("Refresh")
    private val settingsButton = JButton("Settings")

    // Status bar checkboxes
    private val hiddenCheckbox = JBCheckBox("Hidden")
    private val permissionsCheckbox = if (!SystemInfo.isWindows) JBCheckBox("Permissions") else null

    private var showHidden: Boolean = false

    // Store listener references for cleanup in dispose()
    private lateinit var backListener: ActionListener
    private lateinit var forwardListener: ActionListener
    private lateinit var upListener: ActionListener
    private lateinit var homeListener: ActionListener
    private lateinit var refreshListener: ActionListener
    private lateinit var settingsListener: ActionListener
    private lateinit var pathListener: ActionListener
    private lateinit var filterListener: ActionListener
    private lateinit var hiddenListener: ItemListener
    private var permissionsListener: ItemListener? = null

    init {
        currentPath = System.getProperty("user.home")
        history.push(currentPath)

        // Register FileTreeComponent as a child disposable
        Disposer.register(this, fileTreeComponent)

        // Wire double-click on directories to navigate into them
        fileTreeComponent.onDirectoryDoubleClicked = { vf ->
            navigateTo(vf.path)
        }

        // Wire selection changes to update the status bar
        fileTreeComponent.onSelectionChanged = {
            updateStatus()
        }

        // Wire file modification callback to refresh status and bookmarks
        fileTreeComponent.onFilesModified = {
            updateStatus()
            loadBookmarks() // refresh bookmarks in case one was added
        }

        component = buildUI()

        // Store this ExplorerPanel as a client property so actions can find it
        (component as? javax.swing.JComponent)?.putClientProperty(
            ExplorerPanel::class.java.name, this
        )

        // Set initial state
        pathField.text = currentPath
        fileTreeComponent.setRoot(currentPath)
        loadBookmarks()
        updateStatus()
    }

    /**
     * Navigates to the given directory path, updating the tree and path bar.
     * Pushes the path onto the navigation history.
     */
    fun navigateTo(path: String) {
        navigateToInternal(path, pushHistory = true)
    }

    /**
     * Internal navigation method that optionally pushes to history.
     * Used by back/forward buttons to avoid double-pushing.
     */
    private fun navigateToInternal(path: String, pushHistory: Boolean) {
        currentPath = path
        pathField.text = path
        fileTreeComponent.setRoot(path)
        bookmarksPanel.highlightForPath(path)
        if (pushHistory) {
            history.push(path)
        }
        updateHistoryButtons()
        updateStatus()
    }

    /**
     * Updates the enabled state of back/forward buttons based on history.
     */
    private fun updateHistoryButtons() {
        backButton.isEnabled = history.canGoBack
        forwardButton.isEnabled = history.canGoForward
    }

    private fun buildUI(): JComponent {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

        // --- NORTH: Toolbar + Path bar + Filter ---
        val toolbar = buildToolbar()
        mainPanel.add(toolbar, BorderLayout.NORTH)

        // --- CENTER: Splitter (bookmarks | tree) ---
        val splitter = JBSplitter(false, 0.2f)
        splitter.firstComponent = bookmarksPanel.component
        splitter.secondComponent = JBScrollPane(fileTreeComponent.tree)
        mainPanel.add(splitter, BorderLayout.CENTER)

        // --- SOUTH: Status bar ---
        val statusBar = JPanel(BorderLayout())
        statusBar.add(statusLabel, BorderLayout.LINE_START)

        // Right side: checkboxes panel
        val checkboxPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        checkboxPanel.add(hiddenCheckbox)
        if (permissionsCheckbox != null) {
            checkboxPanel.add(permissionsCheckbox)
        }
        statusBar.add(checkboxPanel, BorderLayout.LINE_END)

        mainPanel.add(statusBar, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun buildToolbar(): JComponent {
        val toolbarPanel = JPanel()
        toolbarPanel.layout = BoxLayout(toolbarPanel, BoxLayout.Y_AXIS)

        // Row 1: navigation buttons
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        buttonsPanel.add(backButton)
        buttonsPanel.add(forwardButton)
        buttonsPanel.add(upButton)
        buttonsPanel.add(homeButton)
        buttonsPanel.add(refreshButton)
        buttonsPanel.add(settingsButton)
        buttonsPanel.alignmentX = Component.LEFT_ALIGNMENT
        toolbarPanel.add(buttonsPanel)

        // Row 2: current path (full width)
        pathField.alignmentX = Component.LEFT_ALIGNMENT
        pathField.maximumSize = Dimension(Int.MAX_VALUE, pathField.preferredSize.height)
        toolbarPanel.add(pathField)

        // Row 3: filter field
        filterField.emptyText.text = "Filter (e.g. *.kt)"
        filterField.alignmentX = Component.LEFT_ALIGNMENT
        filterField.maximumSize = Dimension(Int.MAX_VALUE, filterField.preferredSize.height)
        toolbarPanel.add(filterField)

        return toolbarPanel
    }

    private fun wireActions() {
        backListener = ActionListener {
            history.back()?.let { path -> navigateToInternal(path, pushHistory = false) }
        }
        backButton.addActionListener(backListener)

        forwardListener = ActionListener {
            history.forward()?.let { path -> navigateToInternal(path, pushHistory = false) }
        }
        forwardButton.addActionListener(forwardListener)

        upListener = ActionListener {
            val parent = NavigationActions.goToParent(currentPath)
            if (parent != currentPath) {
                navigateTo(parent)
            }
        }
        upButton.addActionListener(upListener)

        homeListener = ActionListener {
            navigateTo(NavigationActions.goHome())
        }
        homeButton.addActionListener(homeListener)

        refreshListener = ActionListener {
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }
        refreshButton.addActionListener(refreshListener)

        settingsListener = ActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "System Explorer")
        }
        settingsButton.addActionListener(settingsListener)

        pathListener = ActionListener {
            val typed = pathField.text.trim()
            if (typed.isNotEmpty()) {
                val vf = LocalFileSystem.getInstance().findFileByPath(typed)
                if (vf != null && vf.isDirectory) {
                    navigateTo(typed)
                }
            }
        }
        pathField.addActionListener(pathListener)

        filterListener = ActionListener {
            fileTreeComponent.filterPattern = filterField.text.trim()
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }
        filterField.addActionListener(filterListener)

        // Wire checkbox listeners
        hiddenListener = ItemListener { e ->
            showHidden = e.stateChange == ItemEvent.SELECTED
            fileTreeComponent.showHidden = showHidden
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }
        hiddenCheckbox.addItemListener(hiddenListener)

        if (permissionsCheckbox != null) {
            permissionsListener = ItemListener { e ->
                val settings = ExplorerSettings.getInstance()
                settings.state.showFilePermissions = e.stateChange == ItemEvent.SELECTED
                fileTreeComponent.tree.repaint()
            }
            permissionsCheckbox.addItemListener(permissionsListener!!)

            // Initialize checkbox state from settings
            permissionsCheckbox.isSelected = ExplorerSettings.getInstance().state.showFilePermissions
        }

        // Set initial button state
        updateHistoryButtons()
    }

    private fun loadBookmarks() {
        try {
            val manager = ro.faur.explorer.model.BookmarkManager.getInstance()
            bookmarksPanel.setBookmarks(manager.getBookmarks())
            bookmarksPanel.highlightForPath(currentPath)
        } catch (e: Exception) {
            LOG.warn("Failed to load bookmarks", e)
        }
    }

    /**
     * Updates the status bar based on the current state.
     *
     * When nothing is selected: shows child count like "3 folders, 2 files"
     * When only files selected: shows "N selected -- SIZE"
     * When only directories selected: shows "N selected -- X items, SIZE"
     * When mixed selection: shows "N selected -- X items in dirs, SIZE"
     */
    internal fun updateStatus() {
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isNotEmpty()) {
            val dirs = selected.filter { it.isDirectory }
            val files = selected.filter { !it.isDirectory }
            val fileSizeBytes = files.sumOf { it.length }
            val dirChildCount = dirs.sumOf { FileSizeFormatter.countDirectChildren(it) }

            statusLabel.text = when {
                dirs.isEmpty() -> {
                    // Only files selected
                    "${selected.size} selected -- ${FileSizeFormatter.format(fileSizeBytes)}"
                }
                files.isEmpty() -> {
                    // Only directories selected
                    val dirSizeBytes = dirs.sumOf { FileSizeFormatter.computeDirectoryImmediateSize(it) }
                    "${selected.size} selected -- $dirChildCount items, ${FileSizeFormatter.format(dirSizeBytes)}"
                }
                else -> {
                    // Mixed selection
                    if (dirChildCount > 0) {
                        "${selected.size} selected -- $dirChildCount items in dirs, ${FileSizeFormatter.format(fileSizeBytes)} in files"
                    } else {
                        "${selected.size} selected -- ${FileSizeFormatter.format(fileSizeBytes)}"
                    }
                }
            }
        } else {
            val children = fileTreeComponent.getRootChildren()
            val folderCount = children.count { it.isDirectory }
            val fileCount = children.count { !it.isDirectory }
            statusLabel.text = "$folderCount folders, $fileCount files"
        }
    }

    /**
     * Returns the current status bar text (for testing).
     */
    fun getStatusText(): String = statusLabel.text

    /**
     * Removes all listeners registered on buttons and fields to prevent memory leaks.
     * Child disposables (FileTreeComponent) are disposed automatically by the Disposer framework.
     */
    override fun dispose() {
        backButton.removeActionListener(backListener)
        forwardButton.removeActionListener(forwardListener)
        upButton.removeActionListener(upListener)
        homeButton.removeActionListener(homeListener)
        refreshButton.removeActionListener(refreshListener)
        settingsButton.removeActionListener(settingsListener)
        pathField.removeActionListener(pathListener)
        filterField.removeActionListener(filterListener)
        hiddenCheckbox.removeItemListener(hiddenListener)
        if (permissionsCheckbox != null && permissionsListener != null) {
            permissionsCheckbox.removeItemListener(permissionsListener!!)
        }
    }

    // Wire actions after all fields are initialized
    init {
        wireActions()
    }
}
