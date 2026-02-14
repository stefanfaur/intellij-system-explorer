package com.github.stefanfaur.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javax.swing.JPanel

class ExplorerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Minimal implementation: just add an empty panel for now
        // Full ExplorerPanel will be added in Task 11
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(JPanel(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}
