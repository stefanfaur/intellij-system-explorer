package com.github.stefanfaur.explorer.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.github.stefanfaur.explorer.actions.NavigationActions
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
 * - A toolbar at the top with navigation buttons (Up, Home, Refresh, Hidden toggle) and an editable path bar
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
    private val statusLabel = JBLabel("Ready")

    // Toolbar buttons
    private val upButton = JButton("Up")
    private val homeButton = JButton("Home")
    private val refreshButton = JButton("Refresh")
    private val hiddenToggle = JToggleButton("Hidden")

    init {
        currentPath = System.getProperty("user.home")
        history.push(currentPath)

        component = buildUI()

        // Set initial state
        pathField.text = currentPath
        fileTreeComponent.setRoot(currentPath)
        loadBookmarks()

        // Wire up toolbar actions
        wireActions()
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

        // --- NORTH: Toolbar + Path bar ---
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

        // Buttons row
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        buttonsPanel.add(upButton)
        buttonsPanel.add(homeButton)
        buttonsPanel.add(refreshButton)
        buttonsPanel.add(hiddenToggle)
        toolbarPanel.add(buttonsPanel, BorderLayout.WEST)

        // Path bar
        toolbarPanel.add(pathField, BorderLayout.CENTER)

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
            // Toggle hidden files — for now, just refresh the tree
            fileTreeComponent.setRoot(currentPath)
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
    }

    private fun loadBookmarks() {
        try {
            val manager = com.github.stefanfaur.explorer.model.BookmarkManager.getInstance()
            bookmarksPanel.setBookmarks(manager.getBookmarks())
        } catch (_: Exception) {
            // BookmarkManager might not be available in test environments
        }
    }

    private fun updateStatus() {
        statusLabel.text = currentPath
    }
}
