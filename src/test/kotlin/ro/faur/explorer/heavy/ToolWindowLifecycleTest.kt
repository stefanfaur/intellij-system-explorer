package ro.faur.explorer.heavy

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ui.ExplorerPanel

class ToolWindowLifecycleTest : BasePlatformTestCase() {

    fun `test ExplorerPanel creates successfully`() {
        val panel = ExplorerPanel(project)
        assertNotNull(panel)
        assertNotNull(panel.component)
    }

    fun `test ExplorerPanel has currentPath`() {
        val panel = ExplorerPanel(project)
        assertTrue(panel.currentPath.isNotEmpty())
    }

    fun `test ExplorerPanel currentPath defaults to project base path`() {
        val panel = ExplorerPanel(project)
        assertEquals(project.basePath, panel.currentPath)
    }

    fun `test ExplorerPanel navigateTo changes currentPath`() {
        val panel = ExplorerPanel(project)
        panel.navigateTo("/tmp")
        assertEquals("/tmp", panel.currentPath)
    }

    fun `test tool window factory creates ExplorerPanel content`() {
        val factory = ro.faur.explorer.ExplorerToolWindowFactory()
        assertNotNull(factory)
        assertTrue(factory is com.intellij.openapi.wm.ToolWindowFactory)
    }

    fun `test BrowserHost starts with one local panel`() {
        val panel = ExplorerPanel(project)
        assertEquals(1, panel.browserHost.panelCount)
        assertEquals("Local", panel.browserHost.localPanel.panelLabel)
    }

    fun `test BrowserHost activePanel is localPanel by default`() {
        val panel = ExplorerPanel(project)
        assertSame(panel.browserHost.localPanel, panel.browserHost.activePanel)
    }
}
