package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import ro.faur.explorer.ui.FileTreeComponent

/**
 * Singleton that determines the currently active panel context in System Explorer.
 *
 * Uses focus hierarchy inspection to detect which panel has keyboard focus.
 * Components register themselves via [registerPanel] and [unregisterPanel].
 */
class ContextResolver : Disposable {

    private val registeredPanels = mutableMapOf<PanelContext, MutableSet<Component>>()
    private var currentContext: PanelContext = PanelContext.UNKNOWN
    
    /** Called when the active context changes */
    var onContextChanged: ((PanelContext) -> Unit)? = null

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("focusOwner") { updateContext() }
    }

    /**
     * Registers a component as belonging to a context.
     */
    fun registerPanel(context: PanelContext, component: Component) {
        registeredPanels.getOrPut(context) { mutableSetOf() }.add(component)
        updateContext()
    }

    /**
     * Unregisters a component from its context.
     */
    fun unregisterPanel(context: PanelContext, component: Component) {
        registeredPanels[context]?.remove(component)
        updateContext()
    }

    /**
     * Returns the currently active context.
     */
    fun getActiveContext(): PanelContext = currentContext

    /**
     * Returns the count of registered components for a given context.
     * Used for testing purposes.
     */
    fun getRegisteredCount(context: PanelContext): Int {
        return registeredPanels[context]?.size ?: 0
    }

    /**
     * Returns true if a component is registered for the given context.
     * Used for testing purposes.
     */
    fun isRegistered(context: PanelContext, component: Component): Boolean {
        return registeredPanels[context]?.contains(component) == true
    }

    /**
     * Checks if a component is currently focused or contained within a focused component.
     */
    fun isComponentActive(component: Component): Boolean {
        val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: return false
        
        return SwingUtilities.isDescendingFrom(component, focusedWindow) ||
               component == focusedWindow
    }

    /**
     * Checks if any text input component is currently focused.
     * Used to prevent chord interception during text editing (e.g., rename dialog).
     * 
     * Note: Quick Open is special - it has a search field but chords should still work.
     */
    fun isTextInputFocused(): Boolean {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: return false
        
        // Check if we're in Quick Open context - if so, allow chords
        if (isInQuickOpenContext(focusOwner)) {
            return false
        }
        
        // Check if focus is in a text component
        val className = focusOwner.javaClass.name.lowercase()
        return className.contains("textfield") ||
               className.contains("textarea") ||
               className.contains("textpane") ||
               className.contains("editorpane") ||
               className.contains("searchfield") ||
               className.contains("jtext")
    }
    
    /**
     * Checks if focus is inside Quick Open context.
     */
    private fun isInQuickOpenContext(focusOwner: Component): Boolean {
        // Check if any registered Quick Open panel contains the focus
        val quickOpenComponents = registeredPanels[PanelContext.QUICK_OPEN] ?: return false
        for (component in quickOpenComponents) {
            if (SwingUtilities.isDescendingFrom(focusOwner, component) || component == focusOwner) {
                return true
            }
        }
        return false
    }

    /**
     * Forces a context update.
     */
    fun updateContext() {
        val previousContext = currentContext
        currentContext = detectActiveContext()
        
        if (currentContext != previousContext) {
            onContextChanged?.invoke(currentContext)
        }
    }

    private fun detectActiveContext(): PanelContext {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: return PanelContext.UNKNOWN
        
        // Check Quick Open first (popup layers are on top)
        if (isInPopupLayer(focusOwner)) {
            // Check if it's our Quick Open popup
            val window = SwingUtilities.getWindowAncestor(focusOwner)
            if (window is javax.swing.JDialog && window.title == "Quick Open") {
                return PanelContext.QUICK_OPEN
            }
            // It's another popup (context menu, etc.) - fall through to check registered panels
        }
        
        // Check registered panels
        for ((context, components) in registeredPanels) {
            for (component in components) {
                // Check if focusOwner is inside the registered component
                if (SwingUtilities.isDescendingFrom(focusOwner, component) || component == focusOwner) {
                    return context
                }
            }
        }
        
        return PanelContext.UNKNOWN
    }

    private fun isInPopupLayer(component: Component): Boolean {
        var parent = component.parent
        while (parent != null) {
            if (parent is JLayeredPane) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    /**
     * Hides speed search popup if it's active in any registered component.
     * Call this before processing chord keys to prevent IntelliJ's speed search
     * from consuming them.
     */
    fun hideSpeedSearch() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: return

        java.lang.System.err.println("DEBUG hideSpeedSearch: focusOwner=${focusOwner?.javaClass?.name}")

        // First try: Look for FileTreeComponent in component hierarchy
        var component: Component? = focusOwner
        while (component != null) {
            if (component is FileTreeComponent) {
                java.lang.System.err.println("DEBUG hideSpeedSearch: FOUND FileTreeComponent in hierarchy")
                component.hideSpeedSearch()
                return
            }
            component = component.parent
        }

        // Second try: If focus is on a JTree, try to find FileTreeComponent via client property
        java.lang.System.err.println("DEBUG hideSpeedSearch: Not found in hierarchy, checking JTree client property")
        if (focusOwner is javax.swing.JTree) {
            val ftc = focusOwner.getClientProperty("FileTreeComponent") as? FileTreeComponent
            if (ftc != null) {
                java.lang.System.err.println("DEBUG hideSpeedSearch: FOUND FileTreeComponent via JTree client property")
                ftc.hideSpeedSearch()
                return
            }
        }

        java.lang.System.err.println("DEBUG hideSpeedSearch: FileTreeComponent NOT FOUND")
    }

    override fun dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removePropertyChangeListener("focusOwner") { updateContext() }
        registeredPanels.clear()
    }

    companion object {
        @JvmStatic val instance = ContextResolver()
    }
}
