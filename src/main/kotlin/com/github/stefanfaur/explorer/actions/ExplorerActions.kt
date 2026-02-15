package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Toggle the System Explorer tool window visibility (Alt+E).
 */
class ToggleExplorerAction : AnAction("Toggle System Explorer") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("System Explorer") ?: return
        if (toolWindow.isVisible) {
            toolWindow.hide()
        } else {
            toolWindow.show()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
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
