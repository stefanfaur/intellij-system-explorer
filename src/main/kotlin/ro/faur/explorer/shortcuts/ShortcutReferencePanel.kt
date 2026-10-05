package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.util.Timer
import java.util.TimerTask
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Dockable panel showing keyboard shortcuts for the current context.
 * 
 * Features:
 * - Auto-highlights section matching current active panel
 * - Collapsible sections for each category
 * - Shows user overrides in bold
 * - Auto-hides after 5 seconds (temporary mode)
 */
class ShortcutReferencePanel(private val project: Project) : Disposable {

    private val chordRegistry = ChordRegistry.getInstance()
    private val contextResolver = ContextResolver.instance
    
    private var currentContext: PanelContext = PanelContext.UNKNOWN
    private var autoHideTimer: Timer? = null
    
    /** Whether the panel is in temporary mode (auto-hides) */
    var isTemporaryMode: Boolean = false
    
    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8)
        background = JBColor(Color(0xFAFAFA), Color(0x2B2B2B))
        isOpaque = true
    }

    private val scrollPane = JBScrollPane(contentPanel).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        border = javax.swing.BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 16
        // Small preferred size so the tool window can shrink freely.
        preferredSize = Dimension(240, 200)
        minimumSize = Dimension(120, 80)
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        add(scrollPane, BorderLayout.CENTER)
        background = JBColor(Color(0xFAFAFA), Color(0x2B2B2B))
    }

    init {
        // Subscribe to context changes
        contextResolver.onContextChanged = { context ->
            currentContext = context
            refreshDisplay()
        }
        
        // Initial display
        currentContext = contextResolver.getActiveContext()
        refreshDisplay()
    }

    /**
     * Shows the panel temporarily (auto-hides after 5 seconds).
     */
    fun showTemporary() {
        isTemporaryMode = true
        autoHideTimer?.cancel()
        autoHideTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    isTemporaryMode = false
                    // Tool window will auto-hide itself
                }
            }, 5000)
        }
    }

    /**
     * Refreshes the shortcut display for the current context.
     */
    fun refreshDisplay() {
        contentPanel.removeAll()
        
        // Context selector header
        val contextLabel = JLabel("Context: ${currentContext.name.replace("_", " ")}").apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }
        contentPanel.add(contextLabel)
        contentPanel.add(Box.createVerticalStrut(8))
        
        // Add sections based on current context
        when (currentContext) {
            PanelContext.LOCAL_BROWSER, PanelContext.REMOTE_BROWSER -> {
                addNavigationSection()
                addFileOpsSection()
            }
            PanelContext.GIT_PANEL -> {
                addNavigationSection()
                addGitSection()
            }
            PanelContext.QUICK_OPEN -> {
                addQuickOpenSection()
            }
            PanelContext.UNKNOWN -> {
                addGlobalShortcutsSection()
                addInExplorerShortcutsSection()
                addAllChordShortcutsSection()
            }
        }
        
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun addNavigationSection() {
        addSectionHeader("Navigation")
        addShortcutRow("Enter", "Open file")
        addShortcutRow("Backspace", "Navigate back")
    }

    private fun addFileOpsSection() {
        addSectionHeader("File Operations")

        val actionIds = listOf(
            "copy", "cut", "paste", "delete", "rename",
            "newFile", "newFolder", "refresh", "open",
            "editInIde", "showInTerminal", "showInExplorer", "copyPath"
        )
        addRowsFromRegistry(currentContext, actionIds)

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    private fun addGitSection() {
        addSectionHeader("Git Operations")

        val actionIds = listOf(
            "cherryPick", "revertChanges", "renameBranch",
            "newBranch", "fetch", "pull", "copyCommitHash"
        )
        addRowsFromRegistry(PanelContext.GIT_PANEL, actionIds)

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    private fun addQuickOpenSection() {
        addSectionHeader("Quick Open")

        val actionIds = listOf(
            "copyPathQuickOpen", "openQuickOpen", "editPathQuickOpen",
            "copyResultQuickOpen", "refreshIndexQuickOpen"
        )
        addRowsFromRegistry(PanelContext.QUICK_OPEN, actionIds)

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    private fun addRowsFromRegistry(context: PanelContext, actionIds: List<String>) {
        for (actionId in actionIds) {
            val chord = chordRegistry.getChordDisplay(context, actionId) ?: continue
            addShortcutRow(chord, getDisplayName(actionId))
        }
    }

    private fun addGlobalShortcutsSection() {
        addSectionHeader("Global")
        val globals = listOf(
            "SystemExplorer.Toggle" to "Toggle System Explorer",
            "SystemExplorer.QuickOpen" to "Quick Open",
            "SystemExplorer.SwitchPanel1" to "Switch to Panel 1 (Local)",
            "SystemExplorer.SwitchPanel2" to "Switch to Panel 2",
            "SystemExplorer.SwitchPanel3" to "Switch to Panel 3",
            "SystemExplorer.SwitchPanel4" to "Switch to Panel 4",
            "SystemExplorer.SwitchPanel5" to "Switch to Panel 5",
        )
        for ((actionId, description) in globals) {
            val shortcut = shortcutText(actionId) ?: continue
            addShortcutRow(shortcut, description)
        }
    }

    private fun addInExplorerShortcutsSection() {
        addSectionHeader("In System Explorer")
        val actions = listOf(
            "SystemExplorer.OpenSelected" to "Open",
            "SystemExplorer.Back" to "Navigate back",
            "SystemExplorer.NavigateUp" to "Navigate up one level",
            "SystemExplorer.RenameFile" to "Rename",
            "SystemExplorer.DeleteFiles" to "Delete",
            "SystemExplorer.RefreshTree" to "Refresh",
        )
        for ((actionId, description) in actions) {
            val shortcut = shortcutText(actionId) ?: continue
            addShortcutRow(shortcut, description)
        }
    }

    private fun addAllChordShortcutsSection() {
        addSectionHeader("Chord — File Operations (Local / Remote)")
        val fileOps = listOf(
            "copy", "cut", "paste", "delete", "rename",
            "newFile", "newFolder", "refresh", "open",
            "editInIde", "showInTerminal", "showInExplorer", "copyPath"
        )
        addRowsFromRegistry(PanelContext.LOCAL_BROWSER, fileOps)

        addSectionHeader("Chord — Git Panel")
        val git = listOf(
            "cherryPick", "revertChanges", "renameBranch",
            "newBranch", "fetch", "pull", "copyCommitHash"
        )
        addRowsFromRegistry(PanelContext.GIT_PANEL, git)

        addSectionHeader("Chord — Quick Open")
        val quickOpen = listOf(
            "copyPathQuickOpen", "openQuickOpen", "editPathQuickOpen",
            "copyResultQuickOpen", "refreshIndexQuickOpen"
        )
        addRowsFromRegistry(PanelContext.QUICK_OPEN, quickOpen)

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    /**
     * Reads the active keymap's shortcut for the given IntelliJ action id and
     * returns the user-facing text (e.g. "⌘⇧P"). Returns null if no shortcut.
     */
    private fun shortcutText(actionId: String): String? {
        val action = ActionManager.getInstance().getAction(actionId) ?: return null
        val keymap = KeymapManager.getInstance().activeKeymap
        val shortcuts = keymap.getShortcuts(actionId).ifEmpty { action.shortcutSet.shortcuts }
        if (shortcuts.isEmpty()) return null
        return shortcuts.joinToString(" / ") { KeymapUtil.getShortcutText(it) }
    }

    private fun addSectionHeader(title: String) {
        val header = JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = JBColor(Color.GRAY, Color.GRAY)
        }
        contentPanel.add(header)
        contentPanel.add(Box.createVerticalStrut(4))
    }

    private fun addShortcutRow(shortcut: String, description: String) {
        val row = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false

            val shortcutLabel = JLabel(shortcut).apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            }
            
            val descLabel = JLabel(description).apply {
                foreground = JBColor(Color.GRAY, Color.GRAY)
            }
            
            add(shortcutLabel, BorderLayout.WEST)
            add(descLabel, BorderLayout.CENTER)
        }
        contentPanel.add(row)
        contentPanel.add(Box.createVerticalStrut(2))
    }

    private fun getDisplayName(actionId: String): String {
        return when (actionId) {
            "copy" -> "Copy"
            "cut" -> "Cut"
            "paste" -> "Paste"
            "delete" -> "Delete"
            "rename" -> "Rename"
            "newFile" -> "New File"
            "newFolder" -> "New Folder"
            "refresh" -> "Refresh"
            "open" -> "Open"
            "editInIde" -> "Edit in IDE"
            "showInTerminal" -> "Show in Terminal"
            "showInExplorer" -> "Show in Explorer"
            "copyPath" -> "Copy Path"
            "cherryPick" -> "Cherry-pick"
            "revertChanges" -> "Revert Changes"
            "renameBranch" -> "Rename Branch"
            "newBranch" -> "New Branch"
            "fetch" -> "Fetch"
            "pull" -> "Pull"
            "copyCommitHash" -> "Copy Commit Hash"
            "copyPathQuickOpen" -> "Copy Path"
            "openQuickOpen" -> "Open"
            "editPathQuickOpen" -> "Edit Path"
            "copyResultQuickOpen" -> "Copy Result"
            "refreshIndexQuickOpen" -> "Refresh Index"
            else -> actionId
        }
    }

    override fun dispose() {
        autoHideTimer?.cancel()
    }
}
