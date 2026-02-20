package ro.faur.explorer.light

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ExplorerToolWindowFactory
import ro.faur.explorer.actions.ExplorerActionUtil
import ro.faur.explorer.actions.ToggleExplorerAction

/**
 * Tests for ToggleExplorerAction.
 *
 * Note: toolWindow.isVisible and toolWindow.isActive are always false in the
 * BasePlatformTestCase headless environment — there is no real Swing frame, so
 * show/hide/activate calls do not update those flags. Tests that need to verify
 * the toggle-visibility behaviour must run as UI (heavy) tests with a real IDE frame.
 * Here we test what IS observable in headless: no exceptions, panel accessibility,
 * and the action's presentation state.
 */
class ToggleExplorerActionTest : BasePlatformTestCase() {

    fun `test action invokes without error when tool window is hidden`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.registerToolWindow("System Explorer") { }

        val factory = ExplorerToolWindowFactory()
        factory.createToolWindowContent(project, toolWindow)

        val action = ToggleExplorerAction()
        val event = createActionEvent(action)

        // Should not throw regardless of window state
        action.actionPerformed(event)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun `test panel is accessible after toggle action activates tool window`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.registerToolWindow("System Explorer") { }

        val factory = ExplorerToolWindowFactory()
        factory.createToolWindowContent(project, toolWindow)

        val action = ToggleExplorerAction()
        val event = createActionEvent(action)

        action.actionPerformed(event)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        // activateAndThen initialises the content; the panel must be findable after.
        val panel = ExplorerActionUtil.activateAndFindPanel(project)
        assertNotNull("ExplorerPanel should be accessible after toggle action", panel)
    }

    fun `test action update is enabled when project is present`() {
        ToolWindowManager.getInstance(project).registerToolWindow("System Explorer") { }

        val action = ToggleExplorerAction()
        val event = createActionEvent(action)

        action.update(event)

        assertTrue("Action should be enabled when a project is available",
            event.presentation.isEnabledAndVisible)
    }

    private fun createActionEvent(action: ToggleExplorerAction): AnActionEvent {
        val dataContext = DataContext { dataId ->
            if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
        }
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext)
    }
}
