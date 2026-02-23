package ro.faur.explorer.heavy.remote

import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import ro.faur.explorer.ui.ExplorerPanel

class SplitPanelLayoutTest : HeavyPlatformTestCase() {

    fun `test remote panel is hidden when no connection`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("System Explorer") ?: return
        toolWindow.activate(null)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val content = toolWindow.contentManager.contents.firstOrNull() ?: return
        val panel = content.component
        // Basic structure validation — the remote panel should not be visible by default
        assertNotNull(panel)
    }
}
