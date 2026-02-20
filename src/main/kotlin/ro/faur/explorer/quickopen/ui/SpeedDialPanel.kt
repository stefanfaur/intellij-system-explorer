package ro.faur.explorer.quickopen.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ro.faur.explorer.quickopen.model.SearchCandidate
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel

class SpeedDialPanel(
    private val items: List<SearchCandidate>,
    private val onActivated: (SearchCandidate) -> Unit
) : JPanel() {

    init {
        layout = BorderLayout()
        if (items.isNotEmpty()) {
            val header = JBLabel("Quick Access — 1–9 to jump").apply {
                border = JBUI.Borders.empty(4, 8)
                foreground = java.awt.Color.GRAY
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
                    addActionListener { onActivated(candidate) }
                }
                gridPanel.add(button)
            }
            add(gridPanel, BorderLayout.CENTER)
        }
    }

    fun activateAt(index: Int) {
        if (index < items.size) onActivated(items[index])
    }

    fun isEmpty() = items.isEmpty()

    private fun truncate(s: String, maxLen: Int) =
        if (s.length <= maxLen) s else "...${s.takeLast(maxLen - 3)}"
}
