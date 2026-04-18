package ro.faur.explorer.shortcuts.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import ro.faur.explorer.actions.ExplorerActionUtil
import ro.faur.explorer.shortcuts.ChordToast
import ro.faur.explorer.shortcuts.actions.ChordShortcutHandler
import ro.faur.explorer.ui.BrowserPanel
import ro.faur.explorer.ui.ExplorerPanel

/**
 * Action implementations for file browser chord shortcuts.
 * 
 * These actions are triggered by chord sequences (e.g., `c for copy).
 * They delegate to the existing IntelliJ actions registered in plugin.xml.
 */
object FileBrowserShortcuts {

    /**
     * Executes an action on the active panel.
     */
    fun executeAction(panel: ExplorerPanel, actionId: String) {
        val activePanel = panel.browserHost.activePanel
        val project = panel.project
        
        when (actionId) {
            // IntelliJ actions - delegate to ActionManager
            "copy" -> invokeAction(project, "SystemExplorer.CopyFiles")
            "cut" -> invokeAction(project, "SystemExplorer.CutFiles")
            "paste" -> invokeAction(project, "SystemExplorer.PasteFiles")
            "delete" -> invokeAction(project, "SystemExplorer.DeleteFiles")
            "rename" -> invokeAction(project, "SystemExplorer.RenameFile")
            "refresh" -> invokeAction(project, "SystemExplorer.RefreshTree")
            "open" -> invokeAction(project, "SystemExplorer.OpenSelected")
            "copyPath" -> invokeAction(project, "SystemExplorer.CopyPath")
            
            // Panel-specific operations via ChordShortcutHandler
            "newFile" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerNewFile()
                else ChordToast.showActionFailed("newFile", "Not available in this context")
            }
            "newFolder" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerNewFolder()
                else ChordToast.showActionFailed("newFolder", "Not available in this context")
            }
            "editInIde" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerEditInIde()
                else ChordToast.showActionFailed("editInIde", "Not available in this context")
            }
            "showInTerminal" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerShowInTerminal()
                else ChordToast.showActionFailed("showInTerminal", "Not available in this context")
            }
            "showInExplorer" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerShowInExplorer()
                else ChordToast.showActionFailed("showInExplorer", "Not available in this context")
            }
            "copyName" -> {
                if (activePanel is ChordShortcutHandler) activePanel.triggerCopyName()
                else ChordToast.showActionFailed("copyName", "Not available in this context")
            }
            
            else -> ChordToast.showActionFailed(actionId, "Unknown action")
        }
    }

    private fun invokeAction(project: Project, actionId: String) {
        try {
            val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            val action = actionManager.getAction(actionId)
            if (action != null) {
                val event = AnActionEvent.createFromAnAction(
                    action, null,
                    com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
                    DataContext.EMPTY_CONTEXT
                )
                action.actionPerformed(event)
            } else {
                ChordToast.showActionFailed(actionId, "Action not found")
            }
        } catch (e: Exception) {
            ChordToast.showActionFailed(actionId, e.message ?: "Unknown error")
        }
    }
}

/**
 * Interface for panels to implement to support chord shortcuts.
 */
interface ChordShortcutHandler {
    fun triggerNewFile()
    fun triggerNewFolder()
    fun triggerEditInIde()
    fun triggerShowInTerminal()
    fun triggerShowInExplorer()
    fun triggerCopyName()
}
