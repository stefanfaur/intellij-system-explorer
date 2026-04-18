package ro.faur.explorer.shortcuts.settings

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.UIManager

/**
 * A UI component that captures keyboard shortcut sequences.
 * 
 * Displays the current shortcut and allows recording a new one by clicking
 * and typing the desired key sequence. Press Escape to cancel recording.
 * 
 * Features:
 * - Click to enter recording mode
 * - Records chord sequences (base key + second key)
 * - Shows clear/reset button
 * - Supports modifier keys
 */
class ShortcutRecorder : JPanel() {

    private val displayLabel = JLabel("", SwingConstants.CENTER).apply {
        font = Font("Monospace", Font.PLAIN, 13)
        foreground = Color.GRAY
        text = CLICK_TO_RECORD
    }

    private var currentShortcut: ShortcutKey? = null
    private var isRecording = false
    private var baseKeyReceived = false
    private var recordedBaseKey: Int = 0
    private var recordedSecondKey: Int = 0

    private var onShortcutChanged: ((ShortcutKey?) -> Unit)? = null

    init {
        layout = java.awt.BorderLayout()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        )
        add(displayLabel, java.awt.BorderLayout.CENTER)
        preferredSize = Dimension(120, 30)

        // Click to start recording
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (!isRecording) {
                    startRecording()
                }
            }
        })

        // Focus handling
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (isRecording) {
                    stopRecording()
                }
            }
        })
    }

    /**
     * Sets the current shortcut to display.
     */
    fun setShortcut(key: ShortcutKey?) {
        currentShortcut = key
        if (key != null) {
            displayLabel.text = key.toDisplayString()
            displayLabel.foreground = UIManager.getColor("TextPane.foreground") ?: Color.BLACK
        } else {
            displayLabel.text = CLICK_TO_RECORD
            displayLabel.foreground = Color.GRAY
        }
        isRecording = false
        baseKeyReceived = false
    }

    /**
     * Gets the currently recorded shortcut.
     */
    fun getShortcut(): ShortcutKey? = currentShortcut

    /**
     * Clears the recorded shortcut.
     */
    fun clear() {
        setShortcut(null)
        onShortcutChanged?.invoke(null)
    }

    /**
     * Sets a callback for when the shortcut changes.
     */
    fun setOnShortcutChangedListener(listener: (ShortcutKey?) -> Unit) {
        onShortcutChanged = listener
    }

    /**
     * Starts recording a new shortcut.
     */
    private fun startRecording() {
        isRecording = true
        baseKeyReceived = false
        displayLabel.text = RECORDING
        displayLabel.foreground = Color.BLUE
        requestFocusInWindow()
        
        // Add key listener
        val keyAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyPress(e)
            }
        }
        
        // Store listener reference for removal
        currentKeyAdapter = keyAdapter
        displayLabel.addKeyListener(keyAdapter)
        
        // We need to enable focus traversal to receive key events
        isFocusable = true
        requestFocusInWindow()
    }

    private var currentKeyAdapter: KeyAdapter? = null

    /**
     * Stops recording and cleans up.
     */
    private fun stopRecording() {
        isRecording = false
        baseKeyReceived = false
        currentKeyAdapter?.let {
            displayLabel.removeKeyListener(it)
        }
        currentKeyAdapter = null
        
        // Restore display
        if (currentShortcut != null) {
            displayLabel.text = currentShortcut!!.toDisplayString()
            displayLabel.foreground = UIManager.getColor("TextPane.foreground") ?: Color.BLACK
        } else {
            displayLabel.text = CLICK_TO_RECORD
            displayLabel.foreground = Color.GRAY
        }
    }

    /**
     * Handles key press during recording.
     */
    private fun handleKeyPress(e: KeyEvent) {
        when {
            // Escape cancels recording
            e.keyCode == KeyEvent.VK_ESCAPE -> {
                stopRecording()
                return
            }
            
            // Backspace clears the shortcut
            e.keyCode == KeyEvent.VK_BACK_SPACE -> {
                clear()
                stopRecording()
                return
            }
            
            // Base key (backtick) received - enter chord mode
            !baseKeyReceived && e.keyCode == ShortcutKey.BASE_KEY -> {
                baseKeyReceived = true
                recordedBaseKey = e.keyCode
                displayLabel.text = CHORD_BASE_RECEIVED
                return
            }
            
            // If in chord mode, record the second key
            baseKeyReceived -> {
                // Ignore modifier-only presses
                if (e.keyCode in listOf(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, 
                                        KeyEvent.VK_ALT, KeyEvent.VK_META)) {
                    return
                }
                
                // Valid second key received
                recordedSecondKey = e.keyCode
                val newShortcut = ShortcutKey(recordedBaseKey, recordedSecondKey, 
                                              currentShortcut?.context ?: 
                                              ro.faur.explorer.shortcuts.PanelContext.UNKNOWN)
                setShortcut(newShortcut)
                onShortcutChanged?.invoke(newShortcut)
                stopRecording()
                return
            }
            
            // If not in chord mode and backtick not pressed, show error
            else -> {
                // Only allow starting with backtick
                if (e.keyCode != ShortcutKey.BASE_KEY) {
                    displayLabel.text = START_WITH_BACKTICK
                    displayLabel.foreground = Color.RED
                    
                    // Reset display after a moment
                    javax.swing.SwingUtilities.invokeLater {
                        if (!isRecording) return@invokeLater
                        displayLabel.text = CLICK_TO_RECORD
                        displayLabel.foreground = Color.GRAY
                    }
                }
            }
        }
    }

    override fun requestFocusInWindow(): Boolean {
        // Redirect focus to the display label which can receive key events
        val result = super.requestFocusInWindow()
        if (result && isRecording) {
            displayLabel.isFocusable = true
            displayLabel.requestFocusInWindow()
        }
        return result
    }

    companion object {
        private const val CLICK_TO_RECORD = "Click to record"
        private const val RECORDING = "Recording..."
        private const val CHORD_BASE_RECEIVED = "` + ?"
        private const val START_WITH_BACKTICK = "Start with `"
    }
}
