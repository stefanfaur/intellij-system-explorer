package com.github.stefanfaur.explorer.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.AlignX
import javax.swing.JCheckBox
import javax.swing.JComponent

class ExplorerConfigurable : Configurable {

    lateinit var showHiddenFilesCheckBox: JCheckBox
        private set
    lateinit var sortFoldersFirstCheckBox: JCheckBox
        private set
    lateinit var confirmDeleteCheckBox: JCheckBox
        private set
    lateinit var deleteToTrashCheckBox: JCheckBox
        private set
    lateinit var defaultRootBrowseField: TextFieldWithBrowseButton
        private set
    lateinit var showFileSizeInTreeCheckBox: JCheckBox
        private set
    lateinit var showFilePermissionsCheckBox: JCheckBox
        private set
    lateinit var expandOnSingleClickCheckBox: JCheckBox
        private set
    lateinit var rememberLastPathCheckBox: JCheckBox
        private set

    // Keep the old field name as an alias for backward compat in existing tests
    val defaultRootField: TextFieldWithBrowseButton get() = defaultRootBrowseField

    private var myPanel: JComponent? = null

    override fun getDisplayName(): String = "System Explorer"

    override fun createComponent(): JComponent {
        showHiddenFilesCheckBox = JCheckBox("Show hidden files")
        sortFoldersFirstCheckBox = JCheckBox("Sort folders first")
        confirmDeleteCheckBox = JCheckBox("Confirm before delete")
        deleteToTrashCheckBox = JCheckBox("Delete to trash")
        showFileSizeInTreeCheckBox = JCheckBox("Show file size in tree")
        showFilePermissionsCheckBox = JCheckBox("Show file permissions")
        expandOnSingleClickCheckBox = JCheckBox("Expand directories on single click")
        rememberLastPathCheckBox = JCheckBox("Remember last visited path")

        defaultRootBrowseField = TextFieldWithBrowseButton()
        defaultRootBrowseField.addBrowseFolderListener(
            "Select Default Root Directory",
            "Choose the default root directory for the file explorer",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        myPanel = panel {
            group("Display") {
                row { cell(showHiddenFilesCheckBox) }
                row { cell(sortFoldersFirstCheckBox) }
                row { cell(showFileSizeInTreeCheckBox) }
                row { cell(showFilePermissionsCheckBox) }
            }
            group("Behavior") {
                row { cell(expandOnSingleClickCheckBox) }
                row { cell(rememberLastPathCheckBox) }
            }
            group("Delete Behavior") {
                row { cell(confirmDeleteCheckBox) }
                row { cell(deleteToTrashCheckBox) }
            }
            group("Paths") {
                row("Default root path:") {
                    cell(defaultRootBrowseField).align(AlignX.FILL)
                }
            }
        }

        return myPanel!!
    }

    override fun isModified(): Boolean {
        if (!::showHiddenFilesCheckBox.isInitialized) return false
        val settings = ExplorerSettings.getInstance()
        return showHiddenFilesCheckBox.isSelected != settings.state.showHiddenFiles ||
                sortFoldersFirstCheckBox.isSelected != settings.state.sortFoldersFirst ||
                confirmDeleteCheckBox.isSelected != settings.state.confirmDelete ||
                deleteToTrashCheckBox.isSelected != settings.state.deleteToTrash ||
                defaultRootBrowseField.text != settings.state.defaultRoot ||
                showFileSizeInTreeCheckBox.isSelected != settings.state.showFileSizeInTree ||
                showFilePermissionsCheckBox.isSelected != settings.state.showFilePermissions ||
                expandOnSingleClickCheckBox.isSelected != settings.state.expandDirectoriesOnSingleClick ||
                rememberLastPathCheckBox.isSelected != settings.state.rememberLastPath
    }

    override fun apply() {
        val settings = ExplorerSettings.getInstance()
        settings.loadState(
            ExplorerSettings.State(
                showHiddenFiles = showHiddenFilesCheckBox.isSelected,
                sortFoldersFirst = sortFoldersFirstCheckBox.isSelected,
                confirmDelete = confirmDeleteCheckBox.isSelected,
                deleteToTrash = deleteToTrashCheckBox.isSelected,
                defaultRoot = defaultRootBrowseField.text,
                showFileSizeInTree = showFileSizeInTreeCheckBox.isSelected,
                showFilePermissions = showFilePermissionsCheckBox.isSelected,
                sortBy = settings.state.sortBy,  // preserve until UI control is added
                expandDirectoriesOnSingleClick = expandOnSingleClickCheckBox.isSelected,
                rememberLastPath = rememberLastPathCheckBox.isSelected,
            )
        )
    }

    override fun reset() {
        val settings = ExplorerSettings.getInstance()
        showHiddenFilesCheckBox.isSelected = settings.state.showHiddenFiles
        sortFoldersFirstCheckBox.isSelected = settings.state.sortFoldersFirst
        confirmDeleteCheckBox.isSelected = settings.state.confirmDelete
        deleteToTrashCheckBox.isSelected = settings.state.deleteToTrash
        defaultRootBrowseField.text = settings.state.defaultRoot
        showFileSizeInTreeCheckBox.isSelected = settings.state.showFileSizeInTree
        showFilePermissionsCheckBox.isSelected = settings.state.showFilePermissions
        expandOnSingleClickCheckBox.isSelected = settings.state.expandDirectoriesOnSingleClick
        rememberLastPathCheckBox.isSelected = settings.state.rememberLastPath
    }

    override fun disposeUIResources() {
        myPanel = null
    }
}
