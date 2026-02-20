package ro.faur.explorer.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import ro.faur.explorer.ui.ExplorerPanel
import ro.faur.explorer.ui.FileTreeComponent
import java.awt.Component
import java.awt.Container

/**
 * Utility object that locates the active [ExplorerPanel] and [FileTreeComponent]
 * from the "System Explorer" tool window so that keyboard-shortcut actions
 * can delegate to the same logic used by the context menu.
 */
object ExplorerActionUtil {

    internal const val TOOL_WINDOW_ID = "System Explorer"

    /**
     * Returns the [ExplorerPanel] currently attached to the System Explorer tool window,
     * or `null` if the tool window is not open or the content cannot be found.
     */
    fun findExplorerPanel(e: AnActionEvent): ExplorerPanel? {
        val project = e.project ?: return null
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        val content = toolWindow.contentManager.selectedContent ?: return null
        val rootComponent = content.component ?: return null
        return findComponentOfType(rootComponent, ExplorerPanel::class.java)
    }

    /**
     * Returns the [FileTreeComponent] from the active ExplorerPanel,
     * or `null` if none is available.
     */
    fun findFileTreeComponent(e: AnActionEvent): FileTreeComponent? {
        return findExplorerPanel(e)?.fileTreeComponent
    }

    /**
     * Activates the System Explorer tool window and returns the ExplorerPanel.
     * Used by global actions that need the panel even when the tool window isn't focused.
     *
     * Ensures the tool window content is initialized before attempting to find the panel
     * by calling [com.intellij.openapi.wm.ToolWindow.activate] which runs the content
     * factory synchronously if it hasn't been called yet.
     */
    fun activateAndFindPanel(project: Project): ExplorerPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        // activate() ensures content is initialized (unlike show() which can be async)
        toolWindow.activate(null)
        val content = toolWindow.contentManager.selectedContent ?: return null
        val rootComponent = content.component ?: return null
        return findComponentOfType(rootComponent, ExplorerPanel::class.java)
    }

    /**
     * Activates the System Explorer tool window and invokes [onReady] with the
     * [ExplorerPanel] once focus has been fully transferred to the tool window.
     *
     * Unlike [activateAndFindPanel], this uses [ToolWindow.activate]'s Runnable
     * parameter so that the callback fires *after* IntelliJ completes its own
     * focus-transfer sequence.  This prevents IntelliJ's activation machinery from
     * overriding a [ExplorerPanel.focusFileTree] call made immediately after
     * activate() returns.
     */
    fun activateAndThen(project: Project, onReady: (ExplorerPanel) -> Unit) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            val content = toolWindow.contentManager.selectedContent ?: return@activate
            val rootComponent = content.component ?: return@activate
            val panel = findComponentOfType(rootComponent, ExplorerPanel::class.java) ?: return@activate
            onReady(panel)
        }
    }

    /**
     * Whether the System Explorer tool window is active (visible and has focus).
     */
    fun isExplorerActive(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return false
        return toolWindow.isVisible && toolWindow.isActive
    }

    /**
     * Recursively searches a Swing component tree for a component associated
     * with the given type. Since ExplorerPanel is not itself a JComponent,
     * we store it as a client property on its root component.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> findComponentOfType(component: Component, type: Class<T>): T? {
        if (component is javax.swing.JComponent) {
            val prop = component.getClientProperty(ExplorerPanel::class.java.name)
            if (type.isInstance(prop)) return prop as T
        }
        if (component is Container) {
            for (child in component.components) {
                val found = findComponentOfType(child, type)
                if (found != null) return found
            }
        }
        return null
    }
}
