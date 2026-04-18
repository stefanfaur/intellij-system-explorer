package ro.faur.explorer.shortcuts

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * ToolWindowFactory for the Shortcut Reference panel.
 * 
 * Registers 'System Explorer Shortcuts' tool window.
 */
class ShortcutReferenceToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val referencePanel = ShortcutReferencePanel(project)
        Disposer.register(project, referencePanel)
        
        val contentFactory = toolWindow.contentManager.factory
        val content = contentFactory.createContent(referencePanel.component, "", false)
        toolWindow.contentManager.addContent(content)
        
        // Register with ShortcutController for auto-show on chord
        ShortcutController.instance.registerReferencePanel(toolWindow, referencePanel)
    }
}
