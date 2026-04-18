package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import ro.faur.explorer.ui.BrowserHost
import ro.faur.explorer.remote.ui.RemoteBrowserPanel
import ro.faur.explorer.quickopen.ui.QuickOpenPanel

/**
 * Main coordinator for the shortcut system.
 * 
 * Initializes and wires together:
 * - ChordRegistry (singleton)
 * - ContextResolver (singleton)
 * - ChordKeyAdapter (IdeEventQueue dispatcher - registered globally)
 * - VisualChordFeedback
 * - ShortcutReferencePanel
 */
class ShortcutController : Disposable {

    private val chordRegistry = ChordRegistry.getInstance()
    private val contextResolver = ContextResolver.instance
    
    private val chordKeyAdapter = ChordKeyAdapter(chordRegistry, contextResolver)
    private val visualChordFeedback = VisualChordFeedback()
    
    private var referenceToolWindow: ToolWindow? = null
    private var referencePanel: ShortcutReferencePanel? = null
    private var isRegistered = false
    
    /** Current project for action dispatch */
    private var currentProject: Project? = null
    
    /** Callback for when a chord action is dispatched */
    var onActionDispatched: ((String) -> Unit)? = null

    init {
        // Set up visual feedback
        chordKeyAdapter.setVisualFeedback(visualChordFeedback)
        
        // Set up action dispatch callback - wire to ExplorerActionRegistry
        chordKeyAdapter.setOnActionDispatched { actionId ->
            // First call any external callbacks
            onActionDispatched?.invoke(actionId)
            // Then dispatch via ExplorerActionRegistry
            currentProject?.let { project ->
                ExplorerActionRegistry.getInstance().executeAction(project, actionId)
            }
        }
    }

    /**
     * Sets the current project for action dispatch.
     */
    fun setProject(project: Project) {
        currentProject = project
        chordKeyAdapter.setProject(project)
    }

    /**
     * Registers a browser host's panels with the context resolver.
     * Also registers the IdeEventQueue dispatcher.
     */
    fun registerBrowserHost(browserHost: BrowserHost, project: Project) {
        currentProject = project
        chordKeyAdapter.setProject(project)
        
        val localPanel = browserHost.localPanel
        
        // Register with context resolver for context detection
        contextResolver.registerPanel(PanelContext.LOCAL_BROWSER, localPanel)
        
        // Register IdeEventQueue dispatcher globally (only once)
        if (!isRegistered) {
            chordKeyAdapter.register()
            isRegistered = true
        }
    }

    /**
     * Unregisters a browser host's panels.
     */
    fun unregisterBrowserHost(browserHost: BrowserHost) {
        val localPanel = browserHost.localPanel
        
        // Unregister from context resolver
        contextResolver.unregisterPanel(PanelContext.LOCAL_BROWSER, localPanel)
        
        // Unregister IdeEventQueue dispatcher
        if (isRegistered) {
            chordKeyAdapter.unregister()
            isRegistered = false
        }
    }

    /**
     * Registers a remote browser panel with context resolver.
     */
    fun registerRemotePanel(panel: RemoteBrowserPanel, project: Project) {
        // Register with context resolver for context detection
        contextResolver.registerPanel(PanelContext.REMOTE_BROWSER, panel)
        
        // Register IdeEventQueue dispatcher globally (only once)
        if (!isRegistered) {
            chordKeyAdapter.register()
            isRegistered = true
        }
    }

    /**
     * Unregisters a remote browser panel.
     */
    fun unregisterRemotePanel(panel: RemoteBrowserPanel) {
        contextResolver.unregisterPanel(PanelContext.REMOTE_BROWSER, panel)
    }

    /**
     * Registers a Quick Open panel with the chord system.
     */
    fun registerQuickOpenPanel(panel: QuickOpenPanel) {
        contextResolver.registerPanel(PanelContext.QUICK_OPEN, panel)
        
        // Register IdeEventQueue dispatcher globally (only once)
        if (!isRegistered) {
            chordKeyAdapter.register()
            isRegistered = true
        }
    }

    /**
     * Unregisters a Quick Open panel from the chord system.
     */
    fun unregisterQuickOpenPanel(panel: QuickOpenPanel) {
        contextResolver.unregisterPanel(PanelContext.QUICK_OPEN, panel)
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
        if (isRegistered) {
            chordKeyAdapter.unregister()
            isRegistered = false
        }
        visualChordFeedback.dispose()
        referenceToolWindow = null
        referencePanel = null
    }

    companion object {
        @JvmStatic val instance = ShortcutController()
    }
}
