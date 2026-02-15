package com.github.stefanfaur.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.github.stefanfaur.explorer.ui.ExplorerPanel

class ExplorerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorerPanel = ExplorerPanel(project)
        Disposer.register(toolWindow.disposable, explorerPanel)
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(explorerPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
