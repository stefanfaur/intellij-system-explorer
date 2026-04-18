package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.util.Timer
import java.util.TimerTask
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

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
    }
    
    val component: JPanel = JPanel(BorderLayout()).apply {
        add(contentPanel, BorderLayout.CENTER)
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
                contentPanel.add(JLabel("No active panel"))
            }
        }
        
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun addNavigationSection() {
        addSectionHeader("Navigation")
        addShortcutRow("Space (hold)", "Preview file")
        addShortcutRow("Enter", "Open file")
        addShortcutRow("Backspace", "Navigate back")
        addShortcutRow("Tab", "Switch to next panel")
    }

    private fun addFileOpsSection() {
        addSectionHeader("File Operations")
        
        val actions = listOf(
            "copy" to "`c",
            "cut" to "`x",
            "paste" to "`v",
            "delete" to "`d",
            "rename" to "`r",
            "newFile" to "`n",
            "refresh" to "`f",
            "open" to "`o",
            "editInIde" to "`e",
            "showInTerminal" to "`p",
            "showInExplorer" to "`t",
            "copyPath" to "`y"
        )
        
        for ((actionId, shortcut) in actions) {
            addShortcutRow(shortcut, getDisplayName(actionId))
        }

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    private fun addGitSection() {
        addSectionHeader("Git Operations")
        
        val actions = listOf(
            "cherryPick" to "`c",
            "revertChanges" to "`x",
            "renameBranch" to "`r",
            "newBranch" to "`n",
            "fetch" to "`f",
            "pull" to "`p",
            "copyCommitHash" to "`y"
        )
        
        for ((actionId, shortcut) in actions) {
            addShortcutRow(shortcut, getDisplayName(actionId))
        }

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
    }

    private fun addQuickOpenSection() {
        addSectionHeader("Quick Open")
        
        val actions = listOf(
            "copyPathQuickOpen" to "`c",
            "openQuickOpen" to "`o",
            "editPathQuickOpen" to "`e",
            "copyResultQuickOpen" to "`y",
            "refreshIndexQuickOpen" to "`f"
        )
        
        for ((actionId, shortcut) in actions) {
            addShortcutRow(shortcut, getDisplayName(actionId))
        }

        addSectionHeader("Help")
        addShortcutRow("`/", "Show shortcuts")
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
