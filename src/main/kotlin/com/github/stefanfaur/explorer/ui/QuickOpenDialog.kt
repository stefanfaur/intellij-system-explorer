package com.github.stefanfaur.explorer.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.github.stefanfaur.explorer.actions.NavigationActions
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListSelectionListener

/**
 * A dialog for quick directory navigation.
 *
 * Displays a text field with a browse button for entering a directory path
 * and a list of recent directories from the navigation history for quick
 * selection. Validates that the entered path is a valid, existing directory
 * before accepting.
 */
class QuickOpenDialog(
    private val project: Project?,
    private val history: NavigationActions.NavigationHistory
) : DialogWrapper(project) {

    private val pathField = TextFieldWithBrowseButton()
    private val listModel = DefaultListModel<String>()
    private val recentList = JBList(listModel)

    init {
        title = "Quick Open Directory"
        pathField.addBrowseFolderListener(
            "Select Directory",
            "Choose a directory to navigate to",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        populateRecentPaths()
        init()
    }

    private fun populateRecentPaths() {
        listModel.clear()
        for (path in history.getRecentPaths()) {
            listModel.addElement(path)
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(450, 300)

        // Path input section
        val pathPanel = JPanel(BorderLayout(4, 0))
        pathPanel.add(JBLabel("Path:"), BorderLayout.WEST)
        pathPanel.add(pathField, BorderLayout.CENTER)
        panel.add(pathPanel, BorderLayout.NORTH)

        // Recent paths section
        val recentPanel = JPanel(BorderLayout(0, 4))
        recentPanel.add(JBLabel("Recent:"), BorderLayout.NORTH)

        recentList.addListSelectionListener(ListSelectionListener {
            val selected = recentList.selectedValue
            if (selected != null) {
                pathField.text = selected
            }
        })

        recentPanel.add(JBScrollPane(recentList), BorderLayout.CENTER)
        panel.add(recentPanel, BorderLayout.CENTER)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val path = getSelectedPath()
        if (path.isEmpty()) {
            return ValidationInfo("Please enter a directory path", pathField)
        }
        if (!isValidPath(path)) {
            return ValidationInfo("'$path' is not a valid directory", pathField)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = pathField.textField

    /**
     * Returns the path entered in the text field, trimmed of whitespace.
     */
    fun getSelectedPath(): String = pathField.text.trim()

    /**
     * Sets the text in the path field. Useful for testing.
     */
    fun setPathText(text: String) {
        pathField.text = text
    }

    /**
     * Returns the list of recent paths shown in the dialog.
     */
    fun getRecentPathsList(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until listModel.size()) {
            result.add(listModel.getElementAt(i))
        }
        return result
    }

    companion object {
        /**
         * Checks whether the given path refers to a valid, existing directory.
         */
        @JvmStatic
        fun isValidPath(path: String): Boolean {
            if (path.isBlank()) return false
            val file = File(path)
            return file.exists() && file.isDirectory
        }
    }
}
