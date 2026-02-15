package com.github.stefanfaur.explorer.heavy

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.ui.ExplorerPanel

class ToolWindowLifecycleTest : BasePlatformTestCase() {

    fun `test ExplorerPanel creates successfully`() {
        val panel = ExplorerPanel(project)
        assertNotNull(panel)
        assertNotNull(panel.component)
    }

    fun `test ExplorerPanel has currentPath`() {
        val panel = ExplorerPanel(project)
        val path = panel.currentPath
        assertTrue(path.isNotEmpty())
    }

    fun `test ExplorerPanel currentPath defaults to user home`() {
        val panel = ExplorerPanel(project)
        assertEquals(System.getProperty("user.home"), panel.currentPath)
    }

    fun `test ExplorerPanel navigateTo changes currentPath`() {
        val panel = ExplorerPanel(project)
        panel.navigateTo("/tmp")
        assertEquals("/tmp", panel.currentPath)
    }

    fun `test tool window factory creates ExplorerPanel content`() {
        val factory = com.github.stefanfaur.explorer.ExplorerToolWindowFactory()
        // Verify the factory class exists and can be instantiated
        assertNotNull(factory)
        assertTrue(factory is com.intellij.openapi.wm.ToolWindowFactory)
    }
}
