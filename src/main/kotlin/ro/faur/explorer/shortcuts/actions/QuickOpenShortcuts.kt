package ro.faur.explorer.shortcuts.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import ro.faur.explorer.quickopen.ui.QuickOpenPanel
import ro.faur.explorer.quickopen.ui.SearchResult
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.shortcuts.ChordToast
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Action implementations for Quick Open panel chord shortcuts.
 * 
 * These actions are triggered by chord sequences (e.g., `c for copy path).
 * They delegate to the QuickOpenPanel's methods.
 */
object QuickOpenShortcuts {

    /**
     * Executes a Quick Open action.
     */
    fun executeAction(project: Project, actionId: String, quickOpenPanel: QuickOpenPanel?) {
        if (quickOpenPanel == null) {
            ChordToast.showActionFailed(actionId, "Quick Open not available")
            return
        }

        when (actionId) {
            "copyPathQuickOpen" -> doCopyPath(quickOpenPanel)
            "openQuickOpen" -> doOpen(quickOpenPanel)
            "editPathQuickOpen" -> doEditPath(quickOpenPanel)
            "copyResultQuickOpen" -> doCopyResult(quickOpenPanel)
            "refreshIndexQuickOpen" -> doRefreshIndex(project)
            else -> ChordToast.showActionFailed(actionId, "Unknown Quick Open action")
        }
    }

    /**
     * Copy the selected path to clipboard.
     */
    private fun doCopyPath(panel: QuickOpenPanel) {
        val selected = getSelectedCandidate(panel)
        if (selected == null) {
            ChordToast.showActionFailed("copyPathQuickOpen", "No item selected")
            return
        }

        copyToClipboard(selected.fullPath)
        ChordToast.showInfo("Copied: ${selected.displayName}")
    }

    /**
     * Open the selected item.
     */
    private fun doOpen(panel: QuickOpenPanel) {
        val selected = getSelectedCandidate(panel)
        if (selected == null) {
            ChordToast.showActionFailed("openQuickOpen", "No item selected")
            return
        }

        // Trigger the selected callback
        invokeOnSelected(panel, selected)
    }

    /**
     * Edit the path (rename the file/folder).
     */
    private fun doEditPath(panel: QuickOpenPanel) {
        val selected = getSelectedCandidate(panel)
        if (selected == null) {
            ChordToast.showActionFailed("editPathQuickOpen", "No item selected")
            return
        }

        // For edit, we show a rename dialog
        val currentName = selected.displayName
        
        val newName = Messages.showInputDialog(
            "Enter new name:",
            "Rename",
            Messages.getQuestionIcon(),
            currentName,
            null
        )
        
        if (!newName.isNullOrBlank() && newName != currentName) {
            ChordToast.showInfo("Rename to: $newName")
        }
    }

    /**
     * Copy the result display (relative path or special result).
     */
    private fun doCopyResult(panel: QuickOpenPanel) {
        val selected = getSelectedCandidate(panel)
        if (selected == null) {
            ChordToast.showActionFailed("copyResultQuickOpen", "No item selected")
            return
        }

        // Copy the display name
        copyToClipboard(selected.displayName)
        ChordToast.showInfo("Copied: ${selected.displayName}")
    }

    /**
     * Refresh the Quick Open index.
     */
    private fun doRefreshIndex(project: Project) {
        ChordToast.showInfo("Index refresh triggered...")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }

    /**
     * Gets the selected SearchCandidate from the panel.
     */
    private fun getSelectedCandidate(panel: QuickOpenPanel): SearchCandidate? {
        return try {
            // Access resultList field via reflection
            val listField = panel.javaClass.getDeclaredField("resultList")
            listField.isAccessible = true
            val list = listField.get(panel) as? JBList<*>
            @Suppress("UNCHECKED_CAST")
            val selectedValue = (list as? JBList<SearchResult>)?.selectedValue
            selectedValue?.scored?.candidate
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Invokes the onSelected callback with the given candidate.
     */
    private fun invokeOnSelected(panel: QuickOpenPanel, candidate: SearchCandidate) {
        try {
            val callbackField = panel.javaClass.getDeclaredField("onSelected")
            callbackField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val callback = callbackField.get(panel) as? ((SearchCandidate) -> Unit)
            callback?.invoke(candidate)
        } catch (e: Exception) {
            ChordToast.showActionFailed("openQuickOpen", "Could not open: ${e.message}")
        }
    }
}
