package ro.faur.explorer.remote.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import ro.faur.explorer.remote.RemoteTailService
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Manages the Remote Logs tool window with ConsoleView-based log tailing tabs.
 *
 * Each tab corresponds to one active `tail -f` session. When a tab is closed by
 * the user (via the content manager's close button), [RemoteTailService.stopTail]
 * is called automatically so the SSH exec channel is cleaned up.
 */
class LogTailToolWindow(
    private val project: Project,
) {
    /**
     * Tracks active console views by key ("serverName:remotePath").
     * Also holds the associated tail service to stop it on tab close.
     */
    private data class TailTab(
        val consoleView: ConsoleView,
        val tailService: RemoteTailService,
        val remotePath: String,
        val content: Content,
    )

    private val activeTabs = mutableMapOf<String, TailTab>()
    private val contentListeners = mutableMapOf<String, ContentManagerListener>()
    private var toolWindowRef: ToolWindow? = null

    /**
     * Opens a new tail tab in [toolWindow] for the given [remotePath] on [serverName].
     *
     * @param toolWindow    The target tool window to add the tab into.
     * @param filename      Display name for the tab (typically the file's base name).
     * @param serverName    Connection name used as a namespace for the tab key.
     * @param remotePath    Full remote path being tailed — passed to [tailService.stopTail] on close.
     * @param tailService   The [RemoteTailService] managing this tail session.
     * @param onLine        Callback that delivers each log line to the caller to start the tail.
     *                      The callback itself receives the [ConsoleView] line printer.
     */
    fun addTailTab(
        toolWindow: ToolWindow,
        filename: String,
        serverName: String,
        remotePath: String,
        tailService: RemoteTailService,
        onLine: ((String) -> Unit) -> Unit,
    ) {
        toolWindowRef = toolWindow
        val tabKey = "$serverName:$remotePath"

        // Close any existing tab for the same path before reopening
        removeTailTab(serverName, remotePath)

        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val panel = JPanel(BorderLayout())
        panel.add(consoleView.component, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "$filename ($serverName)", false)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)

        val tab = TailTab(consoleView, tailService, remotePath, content)
        activeTabs[tabKey] = tab

        // Listen for the content being removed (user clicks the X on the tab)
        val listener = object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content === content) {
                    // Stop the tail when the user closes this tab
                    activeTabs.remove(tabKey)?.let { removed ->
                        removed.tailService.stopTail(removed.remotePath)
                        removed.consoleView.dispose()
                    }
                    contentListeners.remove(tabKey)?.let {
                        toolWindow.contentManager.removeContentManagerListener(it)
                    }
                }
            }
        }
        contentListeners[tabKey] = listener
        toolWindow.contentManager.addContentManagerListener(listener)

        // Begin receiving lines from the tail
        onLine { line ->
            consoleView.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
    }

    /**
     * Programmatically removes the tab for [serverName]:[remotePath], stops the tail,
     * and disposes the console view.
     *
     * Safe to call even if no tab exists for this key.
     */
    fun removeTailTab(serverName: String, remotePath: String) {
        val key = "$serverName:$remotePath"
        val tab = activeTabs.remove(key) ?: return
        tab.tailService.stopTail(tab.remotePath)
        contentListeners.remove(key)?.let { toolWindowRef?.contentManager?.removeContentManagerListener(it) }
        toolWindowRef?.contentManager?.removeContent(tab.content, true)
        tab.consoleView.dispose()
    }

    /**
     * Stops all active tails and disposes all console views.
     * Call this when the plugin is unloaded or the project is closed.
     */
    fun dispose() {
        val snapshot = activeTabs.entries.toList()
        activeTabs.clear()
        contentListeners.clear()
        for ((key, tab) in snapshot) {
            runCatching { tab.tailService.stopTail(tab.remotePath) }
            runCatching {
                contentListeners.remove(key)?.let { toolWindowRef?.contentManager?.removeContentManagerListener(it) }
            }
            runCatching { toolWindowRef?.contentManager?.removeContent(tab.content, true) }
            runCatching { tab.consoleView.dispose() }
        }
    }
}
