package ro.faur.explorer.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

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

    // Display settings
    private lateinit var sortByComboBox: JComboBox<ExplorerSettings.SortBy>
    private lateinit var showFolderItemCountCheckBox: JCheckBox
    private lateinit var statusBarDetailComboBox: JComboBox<String>
    private lateinit var globPresetsTableModel: DefaultTableModel
    private lateinit var globPresetsTable: JBTable

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
        showFolderItemCountCheckBox = JCheckBox("Show item count in folders (e.g. src (12))")

        defaultRootBrowseField = TextFieldWithBrowseButton()
        defaultRootBrowseField.addBrowseFolderListener(
            "Select Default Root Directory",
            "Choose the default root directory for the file explorer",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        sortByComboBox = JComboBox(ExplorerSettings.SortBy.values())
        statusBarDetailComboBox = JComboBox(arrayOf("minimal", "normal", "verbose"))

        globPresetsTableModel = DefaultTableModel(arrayOf("Name", "Pattern"), 0)
        globPresetsTable = JBTable(globPresetsTableModel).apply {
            preferredScrollableViewportSize = Dimension(400, 100)
            putClientProperty("terminateEditOnFocusLost", true)
        }
        val globPresetsDecorator = ToolbarDecorator.createDecorator(globPresetsTable)
            .setAddAction { globPresetsTableModel.addRow(arrayOf("", "")) }
            .setRemoveAction {
                val row = globPresetsTable.selectedRow
                if (row >= 0) globPresetsTableModel.removeRow(row)
            }.createPanel()

        myPanel = panel {
            group("Display") {
                row { cell(showHiddenFilesCheckBox) }
                row { cell(sortFoldersFirstCheckBox) }
                row { cell(showFileSizeInTreeCheckBox) }
                row { cell(showFilePermissionsCheckBox) }
                row { cell(showFolderItemCountCheckBox) }
                row("Sort by:") { cell(sortByComboBox) }
            }
            group("Status Bar") {
                row("Detail level:") { cell(statusBarDetailComboBox) }
                    .rowComment("minimal = item count only; normal = sizes; verbose = sizes + permissions")
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
            group("Glob Filter Presets") {
                row { cell(globPresetsDecorator).align(AlignX.FILL) }
                    .rowComment("Define named glob filter presets for quick access in the toolbar.")
            }
        }

        reset()
        return myPanel!!
    }

    override fun isModified(): Boolean {
        if (!::showHiddenFilesCheckBox.isInitialized) return false
        val settings = ExplorerSettings.getInstance()
        val s = settings.state
        if (showHiddenFilesCheckBox.isSelected != s.showHiddenFiles) return true
        if (sortFoldersFirstCheckBox.isSelected != s.sortFoldersFirst) return true
        if (confirmDeleteCheckBox.isSelected != s.confirmDelete) return true
        if (deleteToTrashCheckBox.isSelected != s.deleteToTrash) return true
        if (defaultRootBrowseField.text != s.defaultRoot) return true
        if (showFileSizeInTreeCheckBox.isSelected != s.showFileSizeInTree) return true
        if (showFilePermissionsCheckBox.isSelected != s.showFilePermissions) return true
        if (expandOnSingleClickCheckBox.isSelected != s.expandDirectoriesOnSingleClick) return true
        if (rememberLastPathCheckBox.isSelected != s.rememberLastPath) return true
        if (!::sortByComboBox.isInitialized) return false
        if (sortByComboBox.selectedItem as? ExplorerSettings.SortBy != s.sortBy) return true
        if (showFolderItemCountCheckBox.isSelected != s.showFolderItemCount) return true
        if (statusBarDetailComboBox.selectedItem as? String != s.statusBarDetail) return true
        if (globPresetsModified(s)) return true
        return false
    }

    private fun globPresetsModified(s: ExplorerSettings.State): Boolean {
        if (!::globPresetsTableModel.isInitialized) return false
        val tablePresets = collectGlobPresets()
        if (tablePresets.size != s.globPresets.size) return true
        return tablePresets.zip(s.globPresets).any { (t, p) -> t.name != p.name || t.pattern != p.pattern }
    }

    private fun collectGlobPresets(): List<GlobPreset> {
        val result = mutableListOf<GlobPreset>()
        for (row in 0 until globPresetsTableModel.rowCount) {
            val name = (globPresetsTableModel.getValueAt(row, 0) as? String)?.trim() ?: continue
            val pattern = (globPresetsTableModel.getValueAt(row, 1) as? String)?.trim() ?: continue
            if (name.isNotBlank()) result.add(GlobPreset(name, pattern))
        }
        return result
    }

    override fun apply() {
        val settings = ExplorerSettings.getInstance()
        val s = settings.state
        s.showHiddenFiles = showHiddenFilesCheckBox.isSelected
        s.sortFoldersFirst = sortFoldersFirstCheckBox.isSelected
        s.confirmDelete = confirmDeleteCheckBox.isSelected
        s.deleteToTrash = deleteToTrashCheckBox.isSelected
        s.defaultRoot = defaultRootBrowseField.text
        s.showFileSizeInTree = showFileSizeInTreeCheckBox.isSelected
        s.showFilePermissions = showFilePermissionsCheckBox.isSelected
        s.expandDirectoriesOnSingleClick = expandOnSingleClickCheckBox.isSelected
        s.rememberLastPath = rememberLastPathCheckBox.isSelected
        if (::sortByComboBox.isInitialized) {
            s.sortBy = sortByComboBox.selectedItem as? ExplorerSettings.SortBy ?: s.sortBy
            s.showFolderItemCount = showFolderItemCountCheckBox.isSelected
            s.statusBarDetail = statusBarDetailComboBox.selectedItem as? String ?: s.statusBarDetail
            s.globPresets = collectGlobPresets().toMutableList()
        }
    }

    override fun reset() {
        if (!::showHiddenFilesCheckBox.isInitialized) return
        val s = ExplorerSettings.getInstance().state
        showHiddenFilesCheckBox.isSelected = s.showHiddenFiles
        sortFoldersFirstCheckBox.isSelected = s.sortFoldersFirst
        confirmDeleteCheckBox.isSelected = s.confirmDelete
        deleteToTrashCheckBox.isSelected = s.deleteToTrash
        defaultRootBrowseField.text = s.defaultRoot
        showFileSizeInTreeCheckBox.isSelected = s.showFileSizeInTree
        showFilePermissionsCheckBox.isSelected = s.showFilePermissions
        expandOnSingleClickCheckBox.isSelected = s.expandDirectoriesOnSingleClick
        rememberLastPathCheckBox.isSelected = s.rememberLastPath
        if (::sortByComboBox.isInitialized) {
            sortByComboBox.selectedItem = s.sortBy
            showFolderItemCountCheckBox.isSelected = s.showFolderItemCount
            statusBarDetailComboBox.selectedItem = s.statusBarDetail
            resetGlobPresets(s)
        }
    }

    private fun resetGlobPresets(s: ExplorerSettings.State) {
        while (globPresetsTableModel.rowCount > 0) globPresetsTableModel.removeRow(0)
        s.globPresets.forEach { preset ->
            globPresetsTableModel.addRow(arrayOf(preset.name, preset.pattern))
        }
    }

    override fun disposeUIResources() {
        myPanel = null
    }
}
