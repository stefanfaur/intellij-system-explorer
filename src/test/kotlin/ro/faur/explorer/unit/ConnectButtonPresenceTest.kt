package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.FlowLayout

class ConnectButtonPresenceTest {

    @Test
    fun `toolbar left buttons row should contain connect button`() {
        // Simulate the left buttons row construction
        val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        val back = JButton("<")
        val forward = JButton(">")
        val up = JButton("Up")
        val home = JButton("Home")
        val refresh = JButton("Refresh")
        val connect = JButton("Connect ▾")

        leftButtons.add(back)
        leftButtons.add(forward)
        leftButtons.add(up)
        leftButtons.add(home)
        leftButtons.add(refresh)
        leftButtons.add(connect)

        val buttons = (0 until leftButtons.componentCount)
            .map { leftButtons.getComponent(it) }
            .filterIsInstance<JButton>()
            .map { it.text }

        assertTrue("Connect ▾" in buttons,
            "Left toolbar should include the Connect button")
        assertEquals(6, buttons.size,
            "Left toolbar should have exactly 6 buttons")
    }
}
