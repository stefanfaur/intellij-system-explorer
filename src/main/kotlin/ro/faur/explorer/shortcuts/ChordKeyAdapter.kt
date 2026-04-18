package ro.faur.explorer.shortcuts

import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities

/**
 * Programmatic KeyAdapter that implements chord-based shortcut detection.
 *
 * Intercepts the base chord key (VK_BACK_QUOTE / backtick) and buffers
 * the next keypress, dispatching to ChordRegistry for resolution.
 *
 * Key features:
 * - 2-second timeout clears chord state
 * - Guards against text input (doesn't intercept during text editing)
 * - Visual feedback integration
 * - Thread-safe state management
 */
class ChordKeyAdapter(
    private val chordRegistry: ChordRegistry,
    private val contextResolver: ContextResolver
) : KeyListener {

    private val timer = Timer("ChordKeyAdapter-Timer", true)
    
    @Volatile private var isChordActive = false
    @Volatile private var baseKeyCode: Int = 0
    @Volatile private var pendingComponent: Component? = null
    
    private var timeoutTask: TimerTask? = null
    private var visualFeedback: VisualChordFeedback? = null
    private var onActionDispatched: ((String) -> Unit)? = null

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
     * Registers this adapter on a component.
     */
    fun register(component: Component) {
        component.addKeyListener(this)
        // If component is a container, register on all children too
        if (component is java.awt.Container) {
            component.addContainerListener(object : java.awt.event.ContainerAdapter() {
                override fun componentAdded(e: java.awt.event.ContainerEvent) {
                    e.child.addKeyListener(this@ChordKeyAdapter)
                }
                override fun componentRemoved(e: java.awt.event.ContainerEvent) {
                    e.child.removeKeyListener(this@ChordKeyAdapter)
                }
            })
        }
    }

    /**
     * Unregisters this adapter from a component.
     */
    fun unregister(component: Component) {
        component.removeKeyListener(this)
        pendingComponent = null
        resetChordState()
    }

    /**
     * Returns whether a chord is currently pending.
     */
    fun isChordActive(): Boolean = isChordActive

    override fun keyPressed(e: KeyEvent) {
        // Don't intercept if text input is focused
        if (contextResolver.isTextInputFocused()) {
            return
        }

        val sourceComponent = SwingUtilities.getRoot(e.component) as? Component ?: return

        if (!isChordActive) {
            // Check for base chord key (backtick)
            if (e.keyCode == ChordAction.BASE_KEY_BACKTICK) {
                activateChordMode(e, sourceComponent)
                e.consume()
                return
            }
        } else {
            // Chord mode active - buffer the second key
            if (e.keyCode == ChordAction.BASE_KEY_BACKTICK) {
                // Double backtick - clear and restart
                resetChordState()
                activateChordMode(e, sourceComponent)
                e.consume()
                return
            }
            
            // Handle escape - cancel chord
            if (e.keyCode == KeyEvent.VK_ESCAPE) {
                resetChordState()
                e.consume()
                return
            }
            
            // Ignore modifier-only presses
            if (isModifierOnly(e)) {
                return
            }
            
            // Dispatch the chord
            dispatchChord(e, sourceComponent)
            e.consume()
        }
    }

    override fun keyReleased(e: KeyEvent) {
        // Not used but required by interface
    }

    override fun keyTyped(e: KeyEvent) {
        // Not used - we use keyPressed for reliable keycode detection
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

    private fun dispatchChord(e: KeyEvent, sourceComponent: Component) {
        val context = contextResolver.getActiveContext()
        val action = chordRegistry.getAction(baseKeyCode, e.keyCode, context)
        
        if (action != null) {
            // Valid action found
            resetChordState()
            onActionDispatched?.invoke(action.actionId)
        } else {
            // Check if chord exists but not for this context
            if (chordRegistry.isChordUsed(baseKeyCode, e.keyCode)) {
                // Chord exists but no action for this context
                resetChordState()
                ChordToast.showNoActionForContext()
            } else {
                // Unknown chord - clear and pass through
                resetChordState()
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

    companion object {
        /** Chord mode timeout in milliseconds */
        const val CHORD_TIMEOUT_MS = 2000L
    }
}
