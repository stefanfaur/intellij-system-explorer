package ro.faur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import ro.faur.explorer.quickopen.ui.QuickOpenPopup
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.ui.QuickOpenDialog

/**
 * Action that opens the Quick Open Directory dialog (Cmd+Shift+P).
 *
 * Works globally — the System Explorer does not need to be focused.
 * When the user confirms a valid directory path, opens/activates the
 * System Explorer and navigates to that directory.
 *
 * Branches on [ExplorerSettings.State.quickOpenV2Enabled]:
 *   - true  → new non-modal [QuickOpenPopup] with fuzzy search
 *   - false → legacy modal [QuickOpenDialog]
 */
class QuickOpenAction : AnAction("Quick Open Directory") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = ExplorerActionUtil.findExplorerPanel(e)
            ?: ExplorerActionUtil.activateAndFindPanel(project)
            ?: return

        val settings = ExplorerSettings.getInstance()
        if (settings.state.quickOpenV2Enabled) {
            QuickOpenPopup.show(project, panel)
        } else {
            val dialog = QuickOpenDialog(project, panel.getNavigationHistory())
            if (dialog.showAndGet()) {
                val path = dialog.getSelectedPath()
                if (path.isNotEmpty()) {
                    panel.navigateTo(path)
                    ToolWindowManager.getInstance(project)
                        .getToolWindow(ExplorerActionUtil.TOOL_WINDOW_ID)?.show()
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable whenever a project is available (global action)
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
