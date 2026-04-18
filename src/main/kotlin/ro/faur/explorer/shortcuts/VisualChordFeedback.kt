package ro.faur.explorer.shortcuts

import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Visual feedback overlay shown during chord mode.
 * 
 * Displays a small floating label (e.g., "Chord mode — press a key…")
 * that appears when backtick is pressed and hides when chord completes or times out.
 */
class VisualChordFeedback : JWindow() {

    private val label = JLabel("Press a key…", SwingConstants.CENTER).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        foreground = JBColor(Color.WHITE, Color.WHITE)
        background = JBColor(Color(50, 50, 50, 220), Color(60, 60, 60, 220))
        isOpaque = true
        border = javax.swing.BorderFactory.createEmptyBorder(8, 16, 8, 16)
    }

    private val content = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(label, BorderLayout.CENTER)
    }

    init {
        isAlwaysOnTop = true
        isFocusable = false
        add(content)
        pack()
    }

    /**
     * Shows the feedback overlay near the component.
     */
    fun show(component: Component) {
        val ownerWindow = SwingUtilities.getWindowAncestor(component)
        if (ownerWindow == null) {
            // Fallback: center on screen
            val screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            setLocation(
                screenBounds.width / 2 - width / 2,
                screenBounds.height / 2 - height / 2
            )
        } else {
            // Position relative to the component's location in screen coordinates
            val compLocation = component.locationOnScreen
            val x = compLocation.x + 20
            val y = compLocation.y + 20
            
            // Keep within screen bounds
            val screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .maximumWindowBounds
            val finalX = x.coerceIn(0, screenBounds.width - width)
            val finalY = y.coerceIn(0, screenBounds.height - height)
            
            setLocation(finalX, finalY)
        }
        
        isVisible = true
        toFront()
    }

    /**
     * Hides the feedback overlay.
     */
    fun hideFeedback() {
        isVisible = false
    }

    /**
     * Updates the label text during chord mode.
     */
    fun setPrompt(text: String) {
        label.text = text
        pack()
    }
}
