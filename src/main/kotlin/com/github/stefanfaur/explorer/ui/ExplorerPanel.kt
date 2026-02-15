package com.github.stefanfaur.explorer.ui

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.github.stefanfaur.explorer.actions.NavigationActions
import com.github.stefanfaur.explorer.util.FileSizeFormatter
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * The main panel for the System Explorer tool window.
 *
 * Contains:
 * - A toolbar at the top with navigation buttons (Up, Home, Refresh, Hidden toggle, Settings) and an editable path bar
 * - A filter text field for glob pattern filtering
 * - A splitter with bookmarks sidebar on the left and a file tree on the right
 * - A status bar at the bottom showing selection info
 *
 * Properties:
 * - [currentPath]: The currently displayed directory path
 * - [component]: The root Swing component to embed in the tool window
 *
 * Methods:
 * - [navigateTo]: Changes the current directory to the given path
 */
class ExplorerPanel(private val project: Project) {

    val component: JComponent

    var currentPath: String
        private set

    private val history = NavigationActions.NavigationHistory()
    private val fileTreeComponent = FileTreeComponent(project)
    private val bookmarksPanel = BookmarksPanel { path -> navigateTo(path) }
    private val pathField = JBTextField()
    private val filterField = JBTextField()
    private val statusLabel = JBLabel("Ready")

    // Toolbar buttons
    private val upButton = JButton("Up")
    private val homeButton = JButton("Home")
    private val refreshButton = JButton("Refresh")
    private val hiddenToggle = JToggleButton("Hidden")
    private val settingsButton = JButton("Settings")

    private var showHidden: Boolean = false

    init {
        currentPath = System.getProperty("user.home")
        history.push(currentPath)

        // Wire double-click on directories to navigate into them
        fileTreeComponent.onDirectoryDoubleClicked = { vf ->
            navigateTo(vf.path)
        }

        // Wire selection changes to update the status bar
        fileTreeComponent.onSelectionChanged = {
            updateStatus()
        }

        component = buildUI()

        // Set initial state
        pathField.text = currentPath
        fileTreeComponent.setRoot(currentPath)
        loadBookmarks()
        updateStatus()
    }

    /**
     * Navigates to the given directory path, updating the tree and path bar.
     */
    fun navigateTo(path: String) {
        currentPath = path
        pathField.text = path
        fileTreeComponent.setRoot(path)
        history.push(path)
        updateStatus()
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
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT))
        statusBar.add(statusLabel)
        mainPanel.add(statusBar, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun buildToolbar(): JComponent {
        val toolbarPanel = JPanel(BorderLayout())

        // Top row: buttons + path bar
        val topRow = JPanel(BorderLayout())
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        buttonsPanel.add(upButton)
        buttonsPanel.add(homeButton)
        buttonsPanel.add(refreshButton)
        buttonsPanel.add(hiddenToggle)
        buttonsPanel.add(settingsButton)
        topRow.add(buttonsPanel, BorderLayout.WEST)
        topRow.add(pathField, BorderLayout.CENTER)

        toolbarPanel.add(topRow, BorderLayout.NORTH)

        // Bottom row: filter field
        filterField.emptyText.text = "Filter (e.g. *.kt)"
        toolbarPanel.add(filterField, BorderLayout.SOUTH)

        return toolbarPanel
    }

    private fun wireActions() {
        upButton.addActionListener {
            val parent = NavigationActions.goToParent(currentPath)
            if (parent != currentPath) {
                navigateTo(parent)
            }
        }

        homeButton.addActionListener {
            navigateTo(NavigationActions.goHome())
        }

        refreshButton.addActionListener {
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }

        hiddenToggle.addActionListener {
            showHidden = hiddenToggle.isSelected
            fileTreeComponent.showHidden = showHidden
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }

        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "System Explorer")
        }

        pathField.addActionListener {
            val typed = pathField.text.trim()
            if (typed.isNotEmpty()) {
                val file = java.io.File(typed)
                if (file.isDirectory) {
                    navigateTo(typed)
                }
            }
        }

        filterField.addActionListener {
            fileTreeComponent.filterPattern = filterField.text.trim()
            fileTreeComponent.setRoot(currentPath)
            updateStatus()
        }
    }

    private fun loadBookmarks() {
        try {
            val manager = com.github.stefanfaur.explorer.model.BookmarkManager.getInstance()
            bookmarksPanel.setBookmarks(manager.getBookmarks())
        } catch (_: Exception) {
            // BookmarkManager might not be available in test environments
        }
    }

    /**
     * Updates the status bar based on the current state.
     *
     * When nothing is selected: shows child count like "3 folders, 2 files"
     * When items are selected: shows "N selected -- SIZE"
     */
    internal fun updateStatus() {
        val selected = fileTreeComponent.getSelectedFiles()
        if (selected.isNotEmpty()) {
            val totalSize = selected.filter { !it.isDirectory }.sumOf { it.length }
            statusLabel.text = "${selected.size} selected -- ${FileSizeFormatter.format(totalSize)}"
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

    // Wire actions after all fields are initialized
    init {
        wireActions()
    }
}
