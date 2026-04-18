package ro.faur.explorer.shortcuts

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to show the shortcut reference panel.
 * Bound to `?` in plugin.xml.
 */
class ShortcutReferencePanelAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        showReferencePanel(project)
    }

    companion object {
        private const val TOOL_WINDOW_ID = "System Explorer Shortcuts"

        /**
         * Shows the shortcut reference panel.
         */
        fun show(project: Project) {
            showReferencePanel(project)
        }

        private fun showReferencePanel(project: Project) {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            var toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
            
            if (toolWindow == null) {
                // Tool window not registered - show notification instead
                ChordToast.showNotification(
                    "Shortcuts Reference",
                    "Shortcut reference panel is not available. Use `?` after pressing backtick.",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                return
            }
            
            // Show and activate the tool window
            toolWindow.show {
                // Set temporary mode for auto-hide
                val panel = ShortcutController.instance.getReferencePanel()
                panel?.showTemporary()
            }
        }
    }
}
