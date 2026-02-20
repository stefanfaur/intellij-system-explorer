package ro.faur.explorer.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import ro.faur.explorer.quickopen.aliases.TeleportAliasStore
import ro.faur.explorer.quickopen.backend.NucleoNative
import ro.faur.explorer.quickopen.backend.RankerSelector
import java.awt.Dimension
import javax.swing.JCheckBox
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
    lateinit var quickOpenV2CheckBox: JCheckBox
        private set

    // Phase 6 — ripgrep & content search fields
    private lateinit var ripgrepPathField: TextFieldWithBrowseButton
    private lateinit var useRipgrepCheckBox: JCheckBox
    private lateinit var contentSearchCheckBox: JCheckBox

    // Scorer status
    private lateinit var scorerStatusLabel: JBLabel

    // Alias management
    private lateinit var aliasTableModel: DefaultTableModel
    private lateinit var aliasTable: JBTable

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
        quickOpenV2CheckBox = JCheckBox("Quick Open v2 — fuzzy search popup (experimental)")

        defaultRootBrowseField = TextFieldWithBrowseButton()
        defaultRootBrowseField.addBrowseFolderListener(
            "Select Default Root Directory",
            "Choose the default root directory for the file explorer",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        // Ripgrep settings
        ripgrepPathField = TextFieldWithBrowseButton()
        ripgrepPathField.addBrowseFolderListener(
            "Select ripgrep executable",
            "Path to the rg binary (leave blank to use system PATH)",
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        useRipgrepCheckBox = JCheckBox("Use ripgrep to enumerate external paths")
        contentSearchCheckBox = JCheckBox("Enable /: content search mode")

        // Alias table
        aliasTableModel = DefaultTableModel(arrayOf("Alias", "Path"), 0)
        aliasTable = JBTable(aliasTableModel).apply {
            preferredScrollableViewportSize = Dimension(400, 120)
            putClientProperty("terminateEditOnFocusLost", true)
        }

        val aliasDecorator = ToolbarDecorator.createDecorator(aliasTable)
            .setAddAction { aliasTableModel.addRow(arrayOf("", "")) }
            .setRemoveAction {
                val row = aliasTable.selectedRow
                if (row >= 0) aliasTableModel.removeRow(row)
            }
            .createPanel()

        val rankerName = try { RankerSelector.active.name } catch (_: Exception) { "Unknown" }
        val scorerText = if (NucleoNative.isAvailable) {
            val platform = System.getProperty("os.name") + " / " + System.getProperty("os.arch")
            "Fuzzy Scorer: $rankerName  \u2713 loaded  ($platform)"
        } else {
            val fallbackNote = if (rankerName.contains("Minuscule")) " \u2014 nucleo unavailable, using fallback" else ""
            "Fuzzy Scorer: $rankerName$fallbackNote"
        }
        scorerStatusLabel = JBLabel(scorerText).apply {
            foreground = if (NucleoNative.isAvailable) JBColor.GREEN.darker() else JBColor.foreground()
        }

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
                row { cell(quickOpenV2CheckBox) }
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
            group("Quick Open — Content Search") {
                row("ripgrep path:") {
                    cell(ripgrepPathField).align(AlignX.FILL)
                }
                row { cell(useRipgrepCheckBox) }
                row { cell(contentSearchCheckBox) }
            }
            group("Quick Open — Fuzzy Scorer") {
                row { cell(scorerStatusLabel) }
            }
            group("Quick Open — Teleport Aliases") {
                row {
                    cell(aliasDecorator).align(AlignX.FILL)
                }
            }
        }

        return myPanel!!
    }

    override fun isModified(): Boolean {
        if (!::showHiddenFilesCheckBox.isInitialized) return false
        val settings = ExplorerSettings.getInstance()
        val baseModified =
            showHiddenFilesCheckBox.isSelected != settings.state.showHiddenFiles ||
            sortFoldersFirstCheckBox.isSelected != settings.state.sortFoldersFirst ||
            confirmDeleteCheckBox.isSelected != settings.state.confirmDelete ||
            deleteToTrashCheckBox.isSelected != settings.state.deleteToTrash ||
            defaultRootBrowseField.text != settings.state.defaultRoot ||
            showFileSizeInTreeCheckBox.isSelected != settings.state.showFileSizeInTree ||
            showFilePermissionsCheckBox.isSelected != settings.state.showFilePermissions ||
            expandOnSingleClickCheckBox.isSelected != settings.state.expandDirectoriesOnSingleClick ||
            rememberLastPathCheckBox.isSelected != settings.state.rememberLastPath ||
            quickOpenV2CheckBox.isSelected != settings.state.quickOpenV2Enabled
        if (baseModified) return true
        if (!::ripgrepPathField.isInitialized) return false
        val ripgrepModified =
            ripgrepPathField.text != settings.state.ripgrepPath ||
            useRipgrepCheckBox.isSelected != settings.state.useRipgrepForExternalPaths ||
            contentSearchCheckBox.isSelected != settings.state.contentSearchEnabled
        if (ripgrepModified) return true
        return aliasesModified()
    }

    private fun aliasesModified(): Boolean {
        if (!::aliasTableModel.isInitialized) return false
        val storeAliases = try { TeleportAliasStore.getInstance().getAliases() } catch (_: Exception) { return false }
        val tableAliases = collectTableAliases()
        if (tableAliases.size != storeAliases.size) return true
        return tableAliases.any { (name, path) -> storeAliases[name] != path }
    }

    private fun collectTableAliases(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (row in 0 until aliasTableModel.rowCount) {
            val name = (aliasTableModel.getValueAt(row, 0) as? String)?.trim() ?: continue
            val path = (aliasTableModel.getValueAt(row, 1) as? String)?.trim() ?: continue
            if (name.isNotBlank()) result[name] = path
        }
        return result
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
                sortBy = settings.state.sortBy,
                expandDirectoriesOnSingleClick = expandOnSingleClickCheckBox.isSelected,
                rememberLastPath = rememberLastPathCheckBox.isSelected,
                quickOpenV2Enabled = quickOpenV2CheckBox.isSelected,
                ripgrepPath = if (::ripgrepPathField.isInitialized) ripgrepPathField.text else settings.state.ripgrepPath,
                useRipgrepForExternalPaths = if (::useRipgrepCheckBox.isInitialized) useRipgrepCheckBox.isSelected else settings.state.useRipgrepForExternalPaths,
                maxIndexSize = settings.state.maxIndexSize,
                allowNetworkMountIndexing = settings.state.allowNetworkMountIndexing,
                contentSearchEnabled = if (::contentSearchCheckBox.isInitialized) contentSearchCheckBox.isSelected else settings.state.contentSearchEnabled,
                contentSearchScope = settings.state.contentSearchScope,
            )
        )

        // Sync alias table to TeleportAliasStore
        if (::aliasTableModel.isInitialized) {
            try {
                val store = TeleportAliasStore.getInstance()
                val existing = store.getAliases().keys.toSet()
                val newAliases = collectTableAliases()
                existing.filter { !newAliases.containsKey(it) }.forEach { store.removeAlias(it) }
                newAliases.forEach { (name, path) -> store.addAlias(name, path) }
            } catch (_: Exception) {}
        }
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
        quickOpenV2CheckBox.isSelected = settings.state.quickOpenV2Enabled
        if (::ripgrepPathField.isInitialized) {
            ripgrepPathField.text = settings.state.ripgrepPath
            useRipgrepCheckBox.isSelected = settings.state.useRipgrepForExternalPaths
            contentSearchCheckBox.isSelected = settings.state.contentSearchEnabled
        }
        if (::aliasTableModel.isInitialized) {
            resetAliasTable()
        }
    }

    private fun resetAliasTable() {
        while (aliasTableModel.rowCount > 0) aliasTableModel.removeRow(0)
        try {
            TeleportAliasStore.getInstance().getAliases().forEach { (name, path) ->
                aliasTableModel.addRow(arrayOf(name, path))
            }
        } catch (_: Exception) {}
    }

    override fun disposeUIResources() {
        myPanel = null
    }
}
