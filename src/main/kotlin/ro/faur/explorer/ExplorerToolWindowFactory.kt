package ro.faur.explorer

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import ro.faur.explorer.ui.ExplorerPanel

class ExplorerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorerPanel = _root_ide_package_.ro.faur.explorer.ui.ExplorerPanel(project)
        Disposer.register(toolWindow.disposable, explorerPanel)
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(explorerPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
