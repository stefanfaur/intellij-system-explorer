package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities

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
     */
    fun isTextInputFocused(): Boolean {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            ?: return false
        
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
            // Might be another popup (context menu, etc.) - don't intercept
            return PanelContext.UNKNOWN
        }
        
        // Check registered panels
        for ((context, components) in registeredPanels) {
            for (component in components) {
                if (SwingUtilities.isDescendingFrom(component, focusOwner) || component == focusOwner) {
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

    override fun dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removePropertyChangeListener("focusOwner") { updateContext() }
        registeredPanels.clear()
    }

    companion object {
        @JvmStatic val instance = ContextResolver()
    }
}
