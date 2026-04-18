package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ide.IdeEventQueue
import java.awt.Component
import java.awt.event.KeyEvent
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities

/**
 * Chord-based shortcut detection using IntelliJ's IdeEventQueue dispatcher.
 *
 * This runs at the IntelliJ event queue level, before IntelliJ processes keyboard shortcuts,
 * so we can properly intercept and consume events.
 */
class ChordKeyAdapter(
    private val chordRegistry: ChordRegistry,
    private val contextResolver: ContextResolver
) : IdeEventQueue.EventDispatcher, Disposable {

    private val timer = Timer("ChordKeyAdapter-Timer", true)
    
    @Volatile private var isChordActive = false
    @Volatile private var baseKeyCode: Int = 0
    @Volatile private var pendingComponent: Component? = null

    // When we consume a KEY_PRESSED for a printable key, AWT still emits a paired
    // KEY_TYPED that would reach the focused JTree and activate IntelliJ's
    // TreeSpeedSearch. We swallow the very next KEY_TYPED to suppress it.
    @Volatile private var swallowNextKeyTyped: Boolean = false
    
    private var timeoutTask: TimerTask? = null
    private var visualFeedback: VisualChordFeedback? = null
    private var onActionDispatched: ((String) -> Unit)? = null
    private var currentProject: Project? = null

    /**
     * Keys that IntelliJ interprets as action keys and processes even after we consume them.
     * For these keys, we need to explicitly block IntelliJ's processing.
     */
    private val intellijActionKeys = setOf(
        KeyEvent.VK_F,    // Search
        KeyEvent.VK_Y,    // Redo
        KeyEvent.VK_C,    // Copy
        KeyEvent.VK_X,    // Cut
        KeyEvent.VK_V,    // Paste
        KeyEvent.VK_Z,    // Undo
        KeyEvent.VK_A     // Select All
    )

    /**
     * Sets the current project for context.
     */
    fun setProject(project: Project) {
        currentProject = project
    }

    /**
     * Callback for when a chord action is successfully dispatched.
     */
    fun setOnActionDispatched(callback: (String) -> Unit) {
        onActionDispatched = callback
    }

    /**
     * Sets the visual feedback component to show/hide during chord mode.
     */
    fun setVisualFeedback(feedback: VisualChordFeedback) {
        visualFeedback = feedback
    }

    /**
     * Registers this dispatcher with IdeEventQueue.
     */
    fun register() {
        IdeEventQueue.getInstance().addDispatcher(this, this)
    }

    /**
     * Unregisters this dispatcher from IdeEventQueue.
     */
    fun unregister() {
        IdeEventQueue.getInstance().removeDispatcher(this)
        resetChordState()
    }

    /**
     * Returns whether a chord is currently pending.
     */
    fun isChordActive(): Boolean = isChordActive

    /**
     * Checks if this key is one that IntelliJ processes specially.
     * These keys need extra handling to ensure they don't trigger IntelliJ actions.
     */
    private fun isIntelliJActionKey(keyCode: Int): Boolean {
        return keyCode in intellijActionKeys
    }

    /**
     * IdeEventQueue.EventDispatcher.dispatch method.
     * Returns true if the event was consumed (processed by us), false otherwise.
     */
    override fun dispatch(e: java.awt.AWTEvent): Boolean {
        if (e !is KeyEvent) return false

        // Swallow the KEY_TYPED paired with a KEY_PRESSED we consumed, so it can't
        // reach the focused JTree and trigger TreeSpeedSearch.
        if (e.id == KeyEvent.KEY_TYPED) {
            if (swallowNextKeyTyped) {
                swallowNextKeyTyped = false
                e.consume()
                return true
            }
            return false
        }

        // Also swallow KEY_RELEASED for a consumed press; otherwise some listeners
        // still react (e.g. mnemonic processing). Cheap to be defensive here.
        if (e.id == KeyEvent.KEY_RELEASED) {
            return false
        }

        if (e.id != KeyEvent.KEY_PRESSED) return false

        // New KEY_PRESSED: the paired KEY_TYPED for any previous consumed press
        // has either already been dispatched or will never arrive.
        swallowNextKeyTyped = false

        // Debug: log chord-related key presses (can be removed once stable)
        if (e.keyCode == ChordAction.BASE_KEY_BACKTICK || isChordActive) {
            java.lang.System.err.println("ChordDispatcher: keyCode=${e.keyCode}, isChordActive=$isChordActive, textFocused=${contextResolver.isTextInputFocused()}, context=${contextResolver.getActiveContext()}")
        }

        // Don't intercept if text input is focused, UNLESS we're already in chord mode
        if (contextResolver.isTextInputFocused() && !isChordActive) {
            return false
        }

        // Check if focus is in one of our registered panels
        val activeContext = contextResolver.getActiveContext()
        if (activeContext == PanelContext.UNKNOWN) {
            // Not in our panels - let IntelliJ handle it
            return false
        }

        // Hide IntelliJ's speed search before processing chord keys
        // This prevents speed search from consuming keys like F for search
        contextResolver.hideSpeedSearch()

        val sourceComponent = SwingUtilities.getRoot(e.component) as? Component ?: return false

        if (!isChordActive) {
            // Check for base chord key (backtick)
            if (e.keyCode == ChordAction.BASE_KEY_BACKTICK) {
                activateChordMode(e, sourceComponent)
                return consumeKeyPressed(e)
            }
            return false  // Let IntelliJ handle other keys
        } else {
            // Chord mode active - buffer the second key
            if (e.keyCode == ChordAction.BASE_KEY_BACKTICK) {
                // Double backtick - clear and restart
                resetChordState()
                activateChordMode(e, sourceComponent)
                return consumeKeyPressed(e)
            }

            // Handle escape - cancel chord
            if (e.keyCode == KeyEvent.VK_ESCAPE) {
                resetChordState()
                return consumeKeyPressed(e)
            }

            // Ignore modifier-only presses
            if (isModifierOnly(e)) {
                return consumeKeyPressed(e)
            }

            // Dispatch the chord
            return dispatchChord(e, sourceComponent)
        }
    }

    /**
     * Consumes a KEY_PRESSED and arms the paired KEY_TYPED to be swallowed,
     * preventing it from reaching the focused component (e.g. TreeSpeedSearch).
     */
    private fun consumeKeyPressed(e: KeyEvent): Boolean {
        e.consume()
        swallowNextKeyTyped = true
        return true
    }

    private fun activateChordMode(e: KeyEvent, sourceComponent: Component) {
        isChordActive = true
        baseKeyCode = e.keyCode
        pendingComponent = sourceComponent
        
        // Show visual feedback
        visualFeedback?.show(sourceComponent)
        
        // Start timeout timer
        startTimeout()
    }

    private fun dispatchChord(e: KeyEvent, sourceComponent: Component): Boolean {
        val context = contextResolver.getActiveContext()
        val action = chordRegistry.getAction(baseKeyCode, e.keyCode, context)
        
        if (action != null) {
            // Consume and arm the KEY_TYPED swallow BEFORE running the action
            // callback. Actions that show a modal dialog pump the event queue
            // on the EDT, which would otherwise dispatch the paired KEY_TYPED
            // while we're still inside this handler and before the flag is set.
            val consumed = consumeKeyPressed(e)
            resetChordState()
            onActionDispatched?.invoke(action.actionId)
            return consumed
        } else {
            // Check if chord exists but not for this context
            if (chordRegistry.isChordUsed(baseKeyCode, e.keyCode)) {
                val consumed = consumeKeyPressed(e)
                resetChordState()
                ChordToast.showNoActionForContext()
                return consumed
            } else {
                // Unknown chord - clear and let it pass through
                resetChordState()
                return false  // Don't consume - let IntelliJ handle
            }
        }
    }

    fun resetChordState() {
        isChordActive = false
        baseKeyCode = 0
        pendingComponent = null
        
        // Cancel timeout
        timeoutTask?.cancel()
        timeoutTask = null
        
        // Hide visual feedback
        visualFeedback?.hideFeedback()
    }

    private fun startTimeout() {
        timeoutTask?.cancel()
        timeoutTask = object : TimerTask() {
            override fun run() {
                SwingUtilities.invokeLater {
                    resetChordState()
                }
            }
        }
        timer.schedule(timeoutTask, CHORD_TIMEOUT_MS)
    }

    private fun isModifierOnly(e: KeyEvent): Boolean {
        return e.keyCode in listOf(
            KeyEvent.VK_SHIFT,
            KeyEvent.VK_CONTROL,
            KeyEvent.VK_ALT,
            KeyEvent.VK_META,
            KeyEvent.VK_ALT_GRAPH
        )
    }

    override fun dispose() {
        unregister()
        timer.cancel()
    }

    companion object {
        /** Chord mode timeout in milliseconds */
        const val CHORD_TIMEOUT_MS = 2000L
    }
}
