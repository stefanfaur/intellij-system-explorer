package ro.faur.explorer.shortcuts

import com.intellij.openapi.project.Project
import ro.faur.explorer.actions.FileActions
import ro.faur.explorer.shortcuts.ChordToast
import ro.faur.explorer.shortcuts.ChordShortcutHandler
import ro.faur.explorer.ui.ExplorerPanel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Action implementations for file browser chord shortcuts.
 * 
 * These actions are triggered by chord sequences (e.g., `c for copy).
 * They call operations directly on the FileTreeComponent to avoid IntelliJ's
 * action system which requires a proper DataContext with project info.
 */
object FileBrowserShortcuts {

    /**
     * Executes an action on the active panel.
     */
    fun executeAction(panel: ExplorerPanel, actionId: String) {
        val activePanel = panel.browserHost.activePanel
        val tree = panel.fileTreeComponent
        
        when (actionId) {
            // File operations - call directly on FileTreeComponent
            "copy" -> {
                val selected = tree.getSelectedFiles()
                if (selected.isNotEmpty()) {
                    FileActions.copyToClipboard(selected)
                    tree.cutFiles = null  // Clear cut state on copy
                    ChordToast.showNotification("Copied", "${selected.size} file(s) copied to clipboard", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("copy", "No files selected")
                }
            }
            "cut" -> {
                val selected = tree.getSelectedFiles()
                if (selected.isNotEmpty()) {
                    tree.cutFiles = selected  // Set cut state
                    ChordToast.showNotification("Cut", "${selected.size} file(s) marked for move", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("cut", "No files selected")
                }
            }
            "paste" -> {
                val contextDir = tree.getContextDirectory()
                if (contextDir != null) {
                    tree.pasteFiles(contextDir)
                    ChordToast.showNotification("Pasted", "Files pasted to ${contextDir.name}", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("paste", "Cannot determine paste location")
                }
            }
            "delete" -> {
                val selected = tree.getSelectedFiles()
                if (selected.isNotEmpty()) {
                    tree.deleteFiles(selected)
                    ChordToast.showNotification("Deleted", "${selected.size} file(s) deleted", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("delete", "No files selected")
                }
            }
            "rename" -> {
                val selected = tree.getSelectedFiles().firstOrNull()
                if (selected != null) {
                    tree.renameFile(selected)
                } else {
                    ChordToast.showActionFailed("rename", "No file selected")
                }
            }
            "refresh" -> {
                tree.refresh()
            }
            "open" -> {
                tree.openSelected()
            }
            "copyPath" -> {
                val selected = tree.getSelectedFiles().firstOrNull()
                if (selected != null) {
                    copyToClipboard(selected.path)
                    ChordToast.showNotification("Copied", "Path: ${selected.path}", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("copyPath", "No file selected")
                }
            }
            "copyName" -> {
                val selected = tree.getSelectedFiles().firstOrNull()
                if (selected != null) {
                    copyToClipboard(selected.name)
                    ChordToast.showNotification("Copied", "Name: ${selected.name}", com.intellij.notification.NotificationType.INFORMATION)
                } else {
                    ChordToast.showActionFailed("copyName", "No file selected")
                }
            }
            
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
            
            else -> ChordToast.showActionFailed(actionId, "Unknown action")
        }
    }
    
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            ChordToast.showActionFailed("copy", e.message ?: "Failed to copy")
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
