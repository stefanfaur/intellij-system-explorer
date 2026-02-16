package ro.faur.explorer.light

import ro.faur.explorer.ExplorerToolWindowFactory
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ToolWindowRegistrationTest : BasePlatformTestCase() {

    fun `test tool window factory is registered as extension point`() {
        val toolWindowEPs = ToolWindowEP.EP_NAME.extensionList
        val explorerEP = toolWindowEPs.find { it.id == "System Explorer" }

        assertNotNull("System Explorer should be registered as a tool window extension point", explorerEP)
        assertEquals(
            "ro.faur.explorer.ExplorerToolWindowFactory",
            explorerEP!!.factoryClass
        )
    }

    fun `test tool window factory creates content`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.registerToolWindow("System Explorer") { }

        val factory = ExplorerToolWindowFactory()
        factory.createToolWindowContent(project, toolWindow)

        val contentManager = toolWindow.contentManager
        assertTrue("Tool window should have at least one content", contentManager.contentCount > 0)
    }

    fun `test tool window factory implements ToolWindowFactory`() {
        val factory = ExplorerToolWindowFactory()
        assertTrue(factory is ToolWindowFactory)
    }
}
