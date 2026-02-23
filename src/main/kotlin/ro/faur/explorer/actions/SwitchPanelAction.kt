package ro.faur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Switches the active panel in [ro.faur.explorer.ui.BrowserHost] to a specific index.
 * Registered as Cmd+1 (index 0 = Local), Cmd+2 (index 1 = first SSH), etc.
 */
open class SwitchPanelAction(private val panelIndex: Int) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e) ?: return
        panel.browserHost.switchToPanel(panelIndex)
    }

    override fun update(e: AnActionEvent) {
        val panel = ExplorerActionUtil.findExplorerPanel(e)
        e.presentation.isEnabled = panel != null &&
                ExplorerActionUtil.isExplorerActive(e) &&
                panelIndex < (panel?.browserHost?.panelCount ?: 0)
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class SwitchPanel1Action : SwitchPanelAction(0)
class SwitchPanel2Action : SwitchPanelAction(1)
class SwitchPanel3Action : SwitchPanelAction(2)
class SwitchPanel4Action : SwitchPanelAction(3)
class SwitchPanel5Action : SwitchPanelAction(4)
