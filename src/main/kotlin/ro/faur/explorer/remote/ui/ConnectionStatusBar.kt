package ro.faur.explorer.remote.ui

import ro.faur.explorer.remote.ConnectionState
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * Green/yellow/red connection status indicator dot.
 */
class ConnectionStatusBar : JComponent() {

    var indicator: ConnectionState.Indicator = ConnectionState.Indicator.RED
        set(value) {
            field = value
            repaint()
        }

    var latencyMs: Int? = null
        set(value) {
            field = value
            toolTipText = when {
                value != null -> "Latency: ${value}ms"
                else -> "Disconnected"
            }
            repaint()
        }

    init {
        preferredSize = Dimension(12, 12)
        minimumSize = Dimension(12, 12)
        maximumSize = Dimension(12, 12)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = when (indicator) {
            ConnectionState.Indicator.GREEN -> Color(0, 180, 0)
            ConnectionState.Indicator.YELLOW -> Color(220, 180, 0)
            ConnectionState.Indicator.RED -> Color(200, 0, 0)
        }
        g2.fillOval(1, 1, width - 2, height - 2)
    }
}
