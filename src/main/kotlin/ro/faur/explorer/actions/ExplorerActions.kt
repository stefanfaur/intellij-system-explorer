package ro.faur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Toggle/focus System Explorer tool window (Alt+E).
 * - If explorer is currently active, hide it.
 * - Otherwise, open and focus it.
 */
class ToggleExplorerAction : AnAction("Toggle System Explorer") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(ExplorerActionUtil.TOOL_WINDOW_ID) ?: return
        if (toolWindow.isVisible && toolWindow.isActive) {
            toolWindow.hide()
            return
        }
        // Use the callback form of activate() so that focusFileTree() fires *after*
        // IntelliJ completes its own focus-transfer sequence.  Calling focusFileTree()
        // synchronously after activate(null) races against IntelliJ's internal focus
        // restoration and loses, leaving the tree unfocused.
        ExplorerActionUtil.activateAndThen(project) { panel ->
            panel.focusFileTree()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Open selected entry in the file tree (Enter).
 */
class OpenSelectedAction : AnAction("Open") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        tree.openSelected()
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().size == 1
    }
}

/**
 * Navigate back in explorer history (Backspace when tree is focused).
 */
class BackInExplorerAction : AnAction("Back") {

    override fun actionPerformed(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e) ?: return
        if (panel.goBack()) {
            panel.focusFileTree()
        }
    }

    override fun update(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e)
        val treeHasFocus = panel?.fileTreeComponent?.tree?.isFocusOwner == true
        e.presentation.isEnabled = panel != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                panel.canGoBack() &&
                treeHasFocus
    }
}

/**
 * Copy selected files to clipboard (Ctrl+C).
 */
class CopyFilesAction : AnAction("Copy") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles()
        if (selected.isNotEmpty()) {
            FileActions.copyToClipboard(selected)
            tree.cutFiles = null
        }
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().isNotEmpty()
    }
}

/**
 * Cut selected files (Ctrl+X) -- copies to clipboard and marks for move on paste.
 */
class CutFilesAction : AnAction("Cut") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles()
        if (selected.isNotEmpty()) {
            FileActions.copyToClipboard(selected)
            tree.cutFiles = selected.toList()
        }
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().isNotEmpty()
    }
}

/**
 * Paste files from clipboard into the context directory (Ctrl+V).
 */
class PasteFilesAction : AnAction("Paste") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val contextDir = tree.getContextDirectory() ?: return
        tree.pasteFiles(contextDir)
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getContextDirectory() != null
    }
}

/**
 * Copy the absolute path of the selected file to the clipboard (Ctrl+Shift+C).
 */
class CopyPathAction : AnAction("Copy Path") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles().firstOrNull() ?: return
        FileActions.copyPathToClipboard(selected)
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().isNotEmpty()
    }
}

/**
 * Rename the selected file (F2).
 */
class RenameFileAction : AnAction("Rename") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles().singleOrNull() ?: return
        tree.renameFile(selected)
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().size == 1
    }
}

/**
 * Delete selected files (Delete key).
 */
class DeleteFilesAction : AnAction("Delete") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles()
        if (selected.isNotEmpty()) {
            tree.deleteFiles(selected)
        }
    }

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabled = tree != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                tree.getSelectedFiles().isNotEmpty()
    }
}

/**
 * Refresh the file tree (F5).
 */
class RefreshTreeAction : AnAction("Refresh") {

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        tree.refresh()
        tree.onFilesModified?.invoke()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = ExplorerActionUtil.findFileTreeComponent(e) != null &&
                ExplorerActionUtil.isExplorerActive(e)
    }
}

/**
 * Navigate up one directory level (Alt+Up when tree is focused).
 */
class NavigateUpAction : AnAction("Navigate Up") {

    override fun actionPerformed(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e) ?: return
        panel.browserHost.activePanel.navigateUp()
    }

    override fun update(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e)
        val treeHasFocus = panel?.fileTreeComponent?.tree?.isFocusOwner == true
        e.presentation.isEnabled = panel != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                treeHasFocus
    }
}
