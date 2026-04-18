package ro.faur.explorer.shortcuts

import com.intellij.openapi.project.Project
import ro.faur.explorer.actions.ExplorerActionUtil
import ro.faur.explorer.shortcuts.actions.FileBrowserShortcuts
import ro.faur.explorer.shortcuts.actions.GitPanelShortcuts
import ro.faur.explorer.shortcuts.actions.QuickOpenShortcuts

/**
 * Bridges chord actions to actual IntelliJ actions and panel operations.
 * 
 * When a chord is dispatched, this registry looks up the action and executes it
 * on the currently active panel.
 */
class ExplorerActionRegistry {

    /**
     * Executes an action by ID on the active panel.
     */
    fun executeAction(project: Project, actionId: String) {
        // Get the ExplorerPanel
        val explorerPanel = ExplorerActionUtil.activateAndFindPanel(project)
        
        when (actionId) {
            // File operations - delegate to existing IntelliJ actions
            "copy" -> invokeAction(project, "SystemExplorer.CopyFiles")
            "cut" -> invokeAction(project, "SystemExplorer.CutFiles")
            "paste" -> invokeAction(project, "SystemExplorer.PasteFiles")
            "delete" -> invokeAction(project, "SystemExplorer.DeleteFiles")
            "rename" -> invokeAction(project, "SystemExplorer.RenameFile")
            "refresh" -> invokeAction(project, "SystemExplorer.RefreshTree")
            "open" -> invokeAction(project, "SystemExplorer.OpenSelected")
            "copyPath" -> invokeAction(project, "SystemExplorer.CopyPath")
            
            // File browser specific - via FileBrowserShortcuts
            "newFile" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            "newFolder" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            "editInIde" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            "showInTerminal" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            "showInExplorer" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            "copyName" -> explorerPanel?.let { FileBrowserShortcuts.executeAction(it, actionId) }
            
            // Git operations - via GitPanelShortcuts (Phase 5)
            "cherryPick" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "revertChanges" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "renameBranch" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "newBranch" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "fetch" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "pull" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            "copyCommitHash" -> {
                val gitPanel = findGitPanel(project)
                GitPanelShortcuts.executeAction(project, actionId, gitPanel)
            }
            
            // Quick Open operations - via QuickOpenShortcuts (Phase 5)
            "copyPathQuickOpen" -> {
                val quickOpenPanel = findQuickOpenPanel(project)
                QuickOpenShortcuts.executeAction(project, actionId, quickOpenPanel)
            }
            "openQuickOpen" -> {
                val quickOpenPanel = findQuickOpenPanel(project)
                QuickOpenShortcuts.executeAction(project, actionId, quickOpenPanel)
            }
            "editPathQuickOpen" -> {
                val quickOpenPanel = findQuickOpenPanel(project)
                QuickOpenShortcuts.executeAction(project, actionId, quickOpenPanel)
            }
            "copyResultQuickOpen" -> {
                val quickOpenPanel = findQuickOpenPanel(project)
                QuickOpenShortcuts.executeAction(project, actionId, quickOpenPanel)
            }
            "refreshIndexQuickOpen" -> {
                val quickOpenPanel = findQuickOpenPanel(project)
                QuickOpenShortcuts.executeAction(project, actionId, quickOpenPanel)
            }
            
            // Panel operations
            "showShortcuts" -> ShortcutReferencePanelAction.show(project)
            
            else -> ChordToast.showActionFailed(actionId, "Unknown action")
        }
    }

    /**
     * Invokes an IntelliJ action by ID.
     */
    private fun invokeAction(project: Project, actionId: String) {
        try {
            val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            val action = actionManager.getAction(actionId)
            if (action != null) {
                val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                    action, null, 
                    com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
                    com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
                )
                action.actionPerformed(event)
            } else {
                ChordToast.showActionFailed(actionId, "Action not found")
            }
        } catch (e: Exception) {
            ChordToast.showActionFailed(actionId, e.message ?: "Unknown error")
        }
    }

    /**
     * Finds the Git panel component if available.
     */
    private fun findGitPanel(project: Project): ro.faur.explorer.gitpanel.ui.GitPanelComponent? {
        return try {
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                .getToolWindow("Git")
            toolWindow?.contentManager?.contents
                ?.firstOrNull()
                ?.component as? ro.faur.explorer.gitpanel.ui.GitPanelComponent
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds the Quick Open panel if available.
     */
    private fun findQuickOpenPanel(project: Project): ro.faur.explorer.quickopen.ui.QuickOpenPanel? {
        return try {
            // QuickOpenPanel tracks itself via companion object
            ro.faur.explorer.quickopen.ui.QuickOpenPanel.activePanel
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @JvmStatic fun getInstance(): ExplorerActionRegistry = instance
        private val instance = ExplorerActionRegistry()
    }
}
