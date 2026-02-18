package ro.faur.explorer.light

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ExplorerToolWindowFactory
import ro.faur.explorer.actions.ToggleExplorerAction

class ToggleExplorerActionTest : BasePlatformTestCase() {

    fun `test Alt+E action does not hide tool window on repeated invocation`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.registerToolWindow("System Explorer") { }

        val factory = ExplorerToolWindowFactory()
        factory.createToolWindowContent(project, toolWindow)

        toolWindow.hide()

        val action = ToggleExplorerAction()
        val event = createActionEvent(action)

        action.actionPerformed(event)
        assertTrue("Tool window should be visible after first invocation", toolWindow.isVisible)

        action.actionPerformed(event)
        assertTrue("Tool window should stay visible after second invocation", toolWindow.isVisible)
    }

    private fun createActionEvent(action: ToggleExplorerAction): AnActionEvent {
        val dataContext = DataContext { dataId ->
            if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
        }
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext)
    }
}
