package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.github.stefanfaur.explorer.ui.QuickOpenDialog

/**
 * Action that opens the Quick Open Directory dialog (Ctrl+Shift+O).
 *
 * Works globally — the System Explorer does not need to be focused.
 * When the user confirms a valid directory path, opens/activates the
 * System Explorer and navigates to that directory.
 */
class QuickOpenAction : AnAction("Quick Open Directory") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Try to find existing panel, or activate the tool window first
        val panel = ExplorerActionUtil.findExplorerPanel(e)
            ?: ExplorerActionUtil.activateAndFindPanel(project)
            ?: return

        val history = panel.getNavigationHistory()

        val dialog = QuickOpenDialog(project, history)
        if (dialog.showAndGet()) {
            val path = dialog.getSelectedPath()
            if (path.isNotEmpty()) {
                panel.navigateTo(path)
                // Ensure the tool window is visible
                ToolWindowManager.getInstance(project)
                    .getToolWindow("System Explorer")
                    ?.show()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable whenever a project is available (global action)
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
