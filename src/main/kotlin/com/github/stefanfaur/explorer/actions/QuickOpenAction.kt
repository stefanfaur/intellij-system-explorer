package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.github.stefanfaur.explorer.ui.QuickOpenDialog

/**
 * Action that opens the Quick Open Directory dialog (Ctrl+Shift+O).
 *
 * When the user confirms a valid directory path in the dialog,
 * navigates the System Explorer tool window to that directory.
 */
class QuickOpenAction : AnAction("Quick Open Directory") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = ExplorerActionUtil.findExplorerPanel(e) ?: return

        // Access the navigation history from the panel
        val history = panel.getNavigationHistory()

        val dialog = QuickOpenDialog(project, history)
        if (dialog.showAndGet()) {
            val path = dialog.getSelectedPath()
            if (path.isNotEmpty()) {
                panel.navigateTo(path)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && ExplorerActionUtil.isExplorerActive(e)
    }
}
