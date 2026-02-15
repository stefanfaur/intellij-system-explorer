package com.github.stefanfaur.explorer.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.AlignX

class ExplorerConfigurable : Configurable {

    lateinit var showHiddenFilesCheckBox: JCheckBox
        private set
    lateinit var sortFoldersFirstCheckBox: JCheckBox
        private set
    lateinit var confirmDeleteCheckBox: JCheckBox
        private set
    lateinit var deleteToTrashCheckBox: JCheckBox
        private set
    lateinit var defaultRootField: JTextField
        private set

    private var myPanel: JComponent? = null

    override fun getDisplayName(): String = "System Explorer"

    override fun createComponent(): JComponent {
        showHiddenFilesCheckBox = JCheckBox("Show hidden files")
        sortFoldersFirstCheckBox = JCheckBox("Sort folders first")
        confirmDeleteCheckBox = JCheckBox("Confirm before delete")
        deleteToTrashCheckBox = JCheckBox("Delete to trash")
        defaultRootField = JTextField()

        myPanel = panel {
            group("Display") {
                row { cell(showHiddenFilesCheckBox) }
                row { cell(sortFoldersFirstCheckBox) }
            }
            group("Delete Behavior") {
                row { cell(confirmDeleteCheckBox) }
                row { cell(deleteToTrashCheckBox) }
            }
            group("Paths") {
                row("Default root path:") {
                    cell(defaultRootField).align(AlignX.FILL)
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
                defaultRootField.text != settings.state.defaultRoot
    }

    override fun apply() {
        val settings = ExplorerSettings.getInstance()
        settings.loadState(
            ExplorerSettings.State(
                showHiddenFiles = showHiddenFilesCheckBox.isSelected,
                sortFoldersFirst = sortFoldersFirstCheckBox.isSelected,
                confirmDelete = confirmDeleteCheckBox.isSelected,
                deleteToTrash = deleteToTrashCheckBox.isSelected,
                defaultRoot = defaultRootField.text,
            )
        )
    }

    override fun reset() {
        val settings = ExplorerSettings.getInstance()
        showHiddenFilesCheckBox.isSelected = settings.state.showHiddenFiles
        sortFoldersFirstCheckBox.isSelected = settings.state.sortFoldersFirst
        confirmDeleteCheckBox.isSelected = settings.state.confirmDelete
        deleteToTrashCheckBox.isSelected = settings.state.deleteToTrash
        defaultRootField.text = settings.state.defaultRoot
    }

    override fun disposeUIResources() {
        myPanel = null
    }
}
