package ro.faur.explorer.quickopen.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ro.faur.explorer.quickopen.model.SearchCandidate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class SpeedDialPanel(
    private val items: List<SearchCandidate>,
    private val onActivated: (SearchCandidate) -> Unit,
    private val onEscapeUp: (() -> Unit)? = null,
) : JPanel() {

    private val buttons = mutableListOf<JButton>()
    private var selectedIndex = -1

    init {
        layout = BorderLayout()
        if (items.isNotEmpty()) {
            val header = JBLabel("Quick Access — ⌘1–9 to jump, ↑↓ to navigate").apply {
                border = JBUI.Borders.empty(4, 8)
                foreground = Color.GRAY
                font = font.deriveFont(10f)
            }
            add(header, BorderLayout.NORTH)

            val gridPanel = JPanel(GridLayout(0, 3, 4, 4)).apply {
                border = JBUI.Borders.empty(4, 8)
            }
            items.forEachIndexed { index, candidate ->
                val displayPath = candidate.fullPath.replace(System.getProperty("user.home"), "~")
                val buttonText = "[${index + 1}] ${truncate(displayPath, 22)}"
                val button = JButton(buttonText).apply {
                    toolTipText = candidate.fullPath
                    isFocusable = false // panel manages focus, not individual buttons
                    addActionListener { onActivated(candidate) }
                }
                buttons.add(button)
                gridPanel.add(button)
            }
            add(gridPanel, BorderLayout.CENTER)

            // Keyboard navigation when this panel has focus
            isFocusable = true
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_DOWN -> {
                            moveSelection(1)
                            e.consume()
                        }
                        KeyEvent.VK_UP -> {
                            if (selectedIndex <= 0) {
                                clearSelection()
                                onEscapeUp?.invoke()
                            } else {
                                moveSelection(-1)
                            }
                            e.consume()
                        }
                        KeyEvent.VK_RIGHT -> {
                            moveSelection(1)
                            e.consume()
                        }
                        KeyEvent.VK_LEFT -> {
                            moveSelection(-1)
                            e.consume()
                        }
                        KeyEvent.VK_ENTER -> {
                            if (selectedIndex in items.indices) {
                                onActivated(items[selectedIndex])
                            }
                            e.consume()
                        }
                        KeyEvent.VK_SLASH -> {
                            clearSelection()
                            onEscapeUp?.invoke()
                            e.consume()
                        }
                    }
                }
            })
        }
    }

    fun activateAt(index: Int) {
        if (index < items.size) onActivated(items[index])
    }

    fun isEmpty() = items.isEmpty()

    /**
     * Selects the first item and requests focus. Called when the user presses
     * Down from the search field while the speed-dial card is visible.
     */
    fun focusAndSelectFirst() {
        if (items.isEmpty()) return
        requestFocus()
        selectedIndex = 0
        updateHighlight()
    }

    fun clearSelection() {
        selectedIndex = -1
        updateHighlight()
    }

    private fun moveSelection(delta: Int) {
        if (items.isEmpty()) return
        val next = (selectedIndex + delta).coerceIn(0, items.size - 1)
        if (next != selectedIndex) {
            selectedIndex = next
            updateHighlight()
        }
    }

    private fun updateHighlight() {
        buttons.forEachIndexed { i, button ->
            if (i == selectedIndex) {
                button.border = BorderFactory.createLineBorder(Color(0x6897BB), 2)
            } else {
                button.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            }
        }
    }

    private fun truncate(s: String, maxLen: Int) =
        if (s.length <= maxLen) s else "...${s.takeLast(maxLen - 3)}"
}
