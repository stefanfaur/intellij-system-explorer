package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import ro.faur.explorer.ui.BrowserHost
import java.awt.Component

/**
 * Main coordinator for the shortcut system.
 * 
 * Initializes and wires together:
 * - ChordRegistry (singleton)
 * - ContextResolver (singleton)
 * - ChordKeyAdapter (per-panel)
 * - PopupChordManager (for Quick Open)
 * - ShortcutReferencePanel
 */
class ShortcutController : Disposable {

    private val chordRegistry = ChordRegistry.getInstance()
    private val contextResolver = ContextResolver.instance
    
    private val chordKeyAdapter = ChordKeyAdapter(chordRegistry, contextResolver)
    private val visualChordFeedback = VisualChordFeedback()
    private val popupChordManager = PopupChordManager(chordKeyAdapter)
    
    private var referenceToolWindow: ToolWindow? = null
    private var referencePanel: ShortcutReferencePanel? = null
    
    /** Callback for when a chord action is dispatched */
    var onActionDispatched: ((String) -> Unit)? = null

    init {
        // Set up visual feedback
        chordKeyAdapter.setVisualFeedback(visualChordFeedback)
        
        // Set up action dispatch callback
        chordKeyAdapter.setOnActionDispatched { actionId ->
            onActionDispatched?.invoke(actionId)
        }
    }

    /**
     * Registers a browser host's panels with the context resolver and chord adapter.
     */
    fun registerBrowserHost(browserHost: BrowserHost, project: Project) {
        val localPanel = browserHost.localPanel
        
        // Register with context resolver
        contextResolver.registerPanel(PanelContext.LOCAL_BROWSER, localPanel)
        
        // Register chord adapter on local panel's tree component
        val treeComponent = getTreeComponent(localPanel)
        if (treeComponent != null) {
            chordKeyAdapter.register(treeComponent)
        }
    }

    /**
     * Unregisters a browser host's panels.
     */
    fun unregisterBrowserHost(browserHost: BrowserHost) {
        val localPanel = browserHost.localPanel
        
        // Unregister from context resolver
        contextResolver.unregisterPanel(PanelContext.LOCAL_BROWSER, localPanel)
        
        // Unregister chord adapter
        val treeComponent = getTreeComponent(localPanel)
        if (treeComponent != null) {
            chordKeyAdapter.unregister(treeComponent)
        }
    }

    /**
     * Registers the shortcut reference panel.
     */
    fun registerReferencePanel(toolWindow: ToolWindow, panel: ShortcutReferencePanel) {
        referenceToolWindow = toolWindow
        referencePanel = panel
    }

    /**
     * Gets the shortcut reference panel.
     */
    fun getReferencePanel(): ShortcutReferencePanel? = referencePanel

    /**
     * Shows the shortcut reference panel temporarily.
     */
    fun showReferencePanelTemporary() {
        referencePanel?.showTemporary()
        referenceToolWindow?.show(null)
    }

    /**
     * Clears the current chord state.
     */
    fun clearChordState() {
        chordKeyAdapter.resetChordState()
    }

    override fun dispose() {
        visualChordFeedback.dispose()
        referenceToolWindow = null
        referencePanel = null
    }

    private fun getTreeComponent(panel: ro.faur.explorer.ui.LocalBrowserPanel): Component? {
        // LocalBrowserPanel exposes getTree() or similar
        // We'll use reflection or a known accessor
        return try {
            val method = panel.javaClass.getMethod("getTreeComponent")
            method.invoke(panel) as? Component
        } catch (e: Exception) {
            // Fallback: try to find JTree in the component hierarchy
            findJTree(panel)
        }
    }

    private fun findJTree(component: Component): Component? {
        if (component is javax.swing.JTree) {
            return component
        }
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findJTree(child)
                if (found != null) return found
            }
        }
        return null
    }

    companion object {
        @JvmStatic val instance = ShortcutController()
    }
}
