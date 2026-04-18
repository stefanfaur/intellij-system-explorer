package ro.faur.explorer.shortcuts.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import ro.faur.explorer.shortcuts.ActionCategory
import ro.faur.explorer.shortcuts.ChordRegistry
import ro.faur.explorer.shortcuts.PanelContext
import ro.faur.explorer.shortcuts.ShortcutSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Main settings panel for customizing keyboard shortcuts.
 * 
 * Features:
 * - Profile selector with create/delete/reset options
 * - Context dropdown to filter shortcuts
 * - Shortcut table with click-to-record editing
 * - Conflict detection and resolution
 * - Import/Export to JSON
 */
class ShortcutSettingsPanel : JPanel(BorderLayout()) {

    private val settings = ShortcutSettings.getInstance()
    private val chordRegistry = ChordRegistry.getInstance()

    // Profile management
    private val profileCombo = JComboBox<String>()
    private val saveProfileButton = JButton("Save")
    private val deleteProfileButton = JButton("Delete")
    private val resetToDefaultsButton = JButton("Reset to Defaults")

    // Context filter
    private val contextCombo = JComboBox<ContextFilter>()
    
    // Shortcut table
    private val tableModel = ShortcutTableModel()
    private val table = JBTable(tableModel).apply {
        preferredScrollableViewportSize = Dimension(500, 400)
        autoCreateRowSorter = true
        columnModel.getColumn(COL_ACTION).preferredWidth = 180
        columnModel.getColumn(COL_SHORTCUT).preferredWidth = 120
        columnModel.getColumn(COL_CONFLICT).preferredWidth = 150
    }
    private val scrollPane = JScrollPane(table)

    // Import/Export
    private val importButton = JButton("Import...")
    private val exportButton = JButton("Export...")

    // Current profile being edited
    private var currentProfile: ShortcutProfile = ShortcutProfile.createDefaultProfile()
    private var isModified = false

    init {
        setupUI()
        loadProfiles()
        loadShortcuts()
    }

    private fun setupUI() {
        // Profile management panel
        val profileTopPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        profileTopPanel.add(JLabel("Profile:"))
        profileTopPanel.add(profileCombo)
        
        val profileControlsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        profileControlsPanel.add(saveProfileButton)
        profileControlsPanel.add(deleteProfileButton)
        profileControlsPanel.add(resetToDefaultsButton)
        
        val profileNorthPanel = JPanel(BorderLayout())
        profileNorthPanel.add(profileTopPanel, BorderLayout.NORTH)
        profileNorthPanel.add(profileControlsPanel, BorderLayout.SOUTH)

        // Context filter
        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        filterPanel.add(JLabel("Context:"))
        contextCombo.model = javax.swing.DefaultComboBoxModel(ContextFilter.entries.toTypedArray())
        contextCombo.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                loadShortcuts()
            }
        }
        filterPanel.add(contextCombo)

        // Table with toolbar
        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction(null)  // Shortcuts are predefined
            .setRemoveAction(null)
            .setEditAction { 
                // Start recording for selected row
                startRecordingForSelectedRow()
            }
        
        val tablePanel = JPanel(BorderLayout())
        tablePanel.add(filterPanel, BorderLayout.NORTH)
        tablePanel.add(decorator.createPanel(), BorderLayout.CENTER)

        // Import/Export panel
        val ioPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        ioPanel.add(importButton)
        ioPanel.add(exportButton)

        // Add all to main panel
        add(profileNorthPanel, BorderLayout.NORTH)
        add(tablePanel, BorderLayout.CENTER)
        add(ioPanel, BorderLayout.SOUTH)

        // Button listeners
        saveProfileButton.addActionListener { saveCurrentProfile() }
        deleteProfileButton.addActionListener { deleteCurrentProfile() }
        resetToDefaultsButton.addActionListener { resetToDefaults() }
        importButton.addActionListener { importSettings() }
        exportButton.addActionListener { exportSettings() }

        // Table cell renderer for shortcuts
        table.columnModel.getColumn(COL_SHORTCUT).cellRenderer = ShortcutCellRenderer()
        
        // Double-click to edit
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    startRecordingForSelectedRow()
                }
            }
        })
    }

    private fun loadProfiles() {
        profileCombo.removeAllItems()
        profileCombo.addItem(ShortcutProfile.DEFAULT_PROFILE_ID)
        
        for (profile in settings.state.profiles) {
            profileCombo.addItem(profile.name)
        }
        
        // Select default
        profileCombo.selectedItem = settings.state.activeProfileId
        
        updateButtonStates()
    }

    private fun loadShortcuts() {
        val filter = contextCombo.selectedItem as? ContextFilter ?: ContextFilter.ALL
        
        val shortcuts = mutableListOf<ShortcutDisplayItem>()
        
        for (action in chordRegistry.getAllActions()) {
            val context = if (filter == ContextFilter.ALL) {
                action.context
            } else {
                PanelContext.entries.find { it.name == filter.name } ?: continue
            }
            
            if (filter != ContextFilter.ALL && action.context != context) {
                continue
            }
            
            // Get current binding from profile
            val key = ShortcutKey(action.baseKeyCode, action.secondKeyCode, action.context)
            val binding = currentProfile.bindings[key]
            
            // Find conflicts
            val conflicts = chordRegistry.getConflicts(action.baseKeyCode, action.secondKeyCode, action.context)
                .filter { it.actionId != action.actionId }
            
            shortcuts.add(ShortcutDisplayItem(
                actionId = action.actionId,
                displayName = action.displayName,
                category = action.category,
                currentShortcut = key,
                isUserOverride = action.isUserOverride,
                conflicts = conflicts.map { it.displayName }
            ))
        }
        
        // Sort by category then display name
        shortcuts.sortWith(compareBy({ it.category.ordinal }, { it.displayName }))
        
        tableModel.setShortcuts(shortcuts)
    }

    private fun startRecordingForSelectedRow() {
        val row = table.selectedRow
        if (row < 0) return
        
        val item = tableModel.getItemAt(table.convertRowIndexToModel(row))
        if (item == null) return
        
        // Show input dialog for the shortcut
        val dialog = ShortcutInputDialog(
            item.displayName,
            item.currentShortcut,
            chordRegistry
        )
        
        if (dialog.showAndGet()) {
            val newShortcut = dialog.resultShortcut
            if (newShortcut != null) {
                // Check for conflicts
                val conflicts = chordRegistry.getConflicts(
                    newShortcut.baseKeyCode,
                    newShortcut.secondKeyCode,
                    newShortcut.context
                ).filter { it.actionId != item.actionId }
                
                if (conflicts.isNotEmpty()) {
                    handleConflict(item, newShortcut, conflicts)
                } else {
                    applyShortcut(item.actionId, newShortcut)
                }
            } else {
                // Clear shortcut
                clearShortcut(item.actionId, item.currentShortcut)
            }
        }
    }

    private fun handleConflict(item: ShortcutDisplayItem, newShortcut: ShortcutKey, conflicts: List<ro.faur.explorer.shortcuts.ChordAction>) {
        val conflictNames = conflicts.joinToString(", ") { it.displayName }
        val result = Messages.showDialog(
            "The shortcut `${newShortcut.toDisplayString()}` is already used by: $conflictNames\n\nWhat would you like to do?",
            "Shortcut Conflict",
            arrayOf("Replace", "Keep Both", "Cancel"),
            0,
            Messages.getQuestionIcon()
        )
        
        when (result) {
            0 -> { // Replace
                applyShortcut(item.actionId, newShortcut)
            }
            1 -> { // Keep Both - just apply without removing from other
                applyShortcut(item.actionId, newShortcut)
            }
            // Cancel - do nothing
        }
    }

    private fun applyShortcut(actionId: String, key: ShortcutKey) {
        // Update profile
        currentProfile.setBinding(key, actionId)
        isModified = true
        
        // Update settings
        settings.setOverride(key.baseKeyCode, key.secondKeyCode, key.context, actionId)
        
        loadShortcuts()
    }

    private fun clearShortcut(actionId: String, key: ShortcutKey?) {
        if (key != null) {
            settings.clearOverride(key.baseKeyCode, key.secondKeyCode, key.context)
            currentProfile.removeBinding(key)
            isModified = true
        }
        loadShortcuts()
    }

    private fun createNewProfile() {
        val name = Messages.showInputDialog(
            "Enter a name for the new profile:",
            "Create Profile",
            Messages.getQuestionIcon()
        )
        
        if (!name.isNullOrBlank()) {
            val id = "custom_${System.currentTimeMillis()}"
            val newProfile = ShortcutProfile(id, name, isBuiltIn = false)
            currentProfile = newProfile
            // Create string-based shortcuts map
            val shortcutsMap = mutableMapOf<String, String>()
            settings.addProfile(
                ShortcutSettings.Profile(
                    id = id,
                    name = name,
                    shortcuts = shortcutsMap
                )
            )
            loadProfiles()
            loadShortcuts()
        }
    }

    private fun saveCurrentProfile() {
        val selectedName = profileCombo.selectedItem as? String ?: return
        
        if (selectedName == ShortcutProfile.DEFAULT_PROFILE_ID) {
            Messages.showMessageDialog(
                "Cannot modify the default profile. Create a new profile to save custom shortcuts.",
                "Cannot Modify Default",
                Messages.getInformationIcon()
            )
            return
        }
        
        // Convert ShortcutKey bindings to string-based map
        val stringShortcuts = currentProfile.bindings.mapKeys { "${it.key.baseKeyCode}:${it.key.secondKeyCode}:${it.key.context.name}" }
        settings.updateProfileShortcuts(stringShortcuts)
        Messages.showMessageDialog(
            "Profile saved successfully.",
            "Profile Saved",
            Messages.getInformationIcon()
        )
    }

    private fun deleteCurrentProfile() {
        val selectedName = profileCombo.selectedItem as? String ?: return
        
        if (selectedName == ShortcutProfile.DEFAULT_PROFILE_ID) {
            Messages.showMessageDialog(
                "Cannot delete the default profile.",
                "Cannot Delete Default",
                Messages.getInformationIcon()
            )
            return
        }
        
        val result = Messages.showYesNoDialog(
            "Are you sure you want to delete the profile '$selectedName'?",
            "Delete Profile",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            val profile = settings.state.profiles.find { it.name == selectedName }
            profile?.let {
                settings.removeProfile(it.id)
                currentProfile = ShortcutProfile.createDefaultProfile()
                loadProfiles()
                loadShortcuts()
            }
        }
    }

    private fun resetToDefaults() {
        val result = Messages.showYesNoDialog(
            "This will reset all shortcuts to their default values. Continue?",
            "Reset to Defaults",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            settings.clearAllOverrides()
            currentProfile = ShortcutProfile.createDefaultProfile()
            isModified = true
            loadShortcuts()
        }
    }

    private fun importSettings() {
        val fileChooser = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        
        FileChooser.chooseFile(fileChooser, null, null) { file ->
            val ioFile = java.io.File(file.path)
            if (settings.importFromJson(ioFile)) {
                Messages.showMessageDialog(
                    "Settings imported successfully.",
                    "Import Successful",
                    Messages.getInformationIcon()
                )
                loadShortcuts()
            } else {
                Messages.showErrorDialog(
                    "Failed to import settings. The file may be corrupted or invalid.",
                    "Import Failed"
                )
            }
        }
    }

    private fun exportSettings() {
        val fileChooser = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        
        FileChooser.chooseFile(fileChooser, null, null) { file ->
            val ioFile = java.io.File(file.path)
            if (settings.exportToJson(ioFile)) {
                Messages.showMessageDialog(
                    "Settings exported successfully to:\n${ioFile.absolutePath}",
                    "Export Successful",
                    Messages.getInformationIcon()
                )
            } else {
                Messages.showErrorDialog(
                    "Failed to export settings.",
                    "Export Failed"
                )
            }
        }
    }

    private fun updateButtonStates() {
        val isDefaultProfile = profileCombo.selectedItem == ShortcutProfile.DEFAULT_PROFILE_ID
        saveProfileButton.isEnabled = !isDefaultProfile && isModified
        deleteProfileButton.isEnabled = !isDefaultProfile
    }

    /**
     * Checks if the settings have been modified.
     */
    fun isModified(): Boolean = isModified

    /**
     * Applies changes.
     */
    fun apply() {
        if (isModified) {
            // Settings are already persisted via the service
        }
    }

    /**
     * Resets to last saved state.
     */
    fun reset() {
        loadProfiles()
        loadShortcuts()
        isModified = false
    }

    // Table model for shortcuts
    private inner class ShortcutTableModel : AbstractTableModel() {
        private val columns = arrayOf("Action", "Shortcut", "Conflicts")
        private val shortcuts = mutableListOf<ShortcutDisplayItem>()

        fun setShortcuts(items: List<ShortcutDisplayItem>) {
            shortcuts.clear()
            shortcuts.addAll(items)
            fireTableDataChanged()
        }

        fun getItemAt(row: Int): ShortcutDisplayItem? {
            return if (row in shortcuts.indices) shortcuts[row] else null
        }

        override fun getRowCount(): Int = shortcuts.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(col: Int): String = columns[col]

        override fun getValueAt(row: Int, col: Int): Any {
            val item = shortcuts[row]
            return when (col) {
                COL_ACTION -> item.displayName
                COL_SHORTCUT -> item.currentShortcut?.toDisplayString() ?: "(none)"
                COL_CONFLICT -> if (item.conflicts.isNotEmpty()) item.conflicts.joinToString(", ") else ""
                else -> ""
            }
        }

        override fun isCellEditable(row: Int, col: Int): Boolean = col == COL_SHORTCUT
    }

    // Cell renderer for shortcuts
    private inner class ShortcutCellRenderer : DefaultTableCellRenderer() {
        override fun setValue(value: Any?) {
            text = value as? String ?: ""
            foreground = when {
                text.startsWith("`") && text.length > 1 -> Color(0x0066CC)
                text == "(none)" -> Color.GRAY
                text.isNotEmpty() -> Color.RED  // Conflict
                else -> Color.GRAY
            }
        }
    }

    companion object {
        private const val COL_ACTION = 0
        private const val COL_SHORTCUT = 1
        private const val COL_CONFLICT = 2
    }
}

/**
 * Filter options for the context dropdown.
 */
enum class ContextFilter {
    ALL,
    LOCAL_BROWSER,
    REMOTE_BROWSER,
    GIT_PANEL,
    QUICK_OPEN
}

/**
 * Data class for display in the shortcuts table.
 */
data class ShortcutDisplayItem(
    val actionId: String,
    val displayName: String,
    val category: ActionCategory,
    val currentShortcut: ShortcutKey?,
    val isUserOverride: Boolean,
    val conflicts: List<String>
)

/**
 * Input dialog for recording a new shortcut.
 */
class ShortcutInputDialog(
    private val actionName: String,
    private val currentShortcut: ShortcutKey?,
    private val chordRegistry: ChordRegistry
) : com.intellij.openapi.ui.DialogBuilder() {
    
    var resultShortcut: ShortcutKey? = currentShortcut
        private set
    
    private val recorder = ShortcutRecorder()
    
    init {
        title("Record Shortcut for '$actionName'")
        
        val panel = JPanel(BorderLayout(8, 8))
        panel.add(JLabel("Click the field below and press the chord sequence (e.g., `c):"), BorderLayout.NORTH)
        panel.add(recorder, BorderLayout.CENTER)
        panel.add(JLabel("Press Escape to cancel, Backspace to clear.", JLabel.CENTER).apply {
            foreground = Color.GRAY
        }, BorderLayout.SOUTH)
        
        recorder.setShortcut(currentShortcut)
        recorder.setOnShortcutChangedListener { key ->
            resultShortcut = key
        }
        
        setCenterPanel(panel)
        addOkAction()
        addCancelAction()
    }
}
