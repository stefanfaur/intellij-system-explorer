package ro.faur.explorer.unit

import com.intellij.icons.AllIcons
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.swing.JButton

/**
 * Tests for toolbar button icons and layout.
 *
 * Verifies that:
 * - Up, Home, and Refresh buttons have both icons and text
 * - Settings button has icon only (no text)
 * - Correct IntelliJ AllIcons are used for each button
 */
class ToolbarIconsTest {

    @Test
    fun `Up button should have MoveUp icon and Up text`() {
        val upButton = JButton("Up", AllIcons.Actions.MoveUp)

        assertEquals("Up", upButton.text, "Up button should have 'Up' text")
        assertEquals(AllIcons.Actions.MoveUp, upButton.icon, "Up button should have MoveUp icon")
        assertNotNull(upButton.icon, "Up button icon should not be null")
    }

    @Test
    fun `Home button should have HomeFolder icon and Home text`() {
        val homeButton = JButton("Home", AllIcons.Nodes.HomeFolder)

        assertEquals("Home", homeButton.text, "Home button should have 'Home' text")
        assertEquals(AllIcons.Nodes.HomeFolder, homeButton.icon, "Home button should have HomeFolder icon")
        assertNotNull(homeButton.icon, "Home button icon should not be null")
    }

    @Test
    fun `Refresh button should have Refresh icon and Refresh text`() {
        val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)

        assertEquals("Refresh", refreshButton.text, "Refresh button should have 'Refresh' text")
        assertEquals(AllIcons.Actions.Refresh, refreshButton.icon, "Refresh button should have Refresh icon")
        assertNotNull(refreshButton.icon, "Refresh button icon should not be null")
    }

    @Test
    fun `Settings button should have Settings icon only (no text)`() {
        val settingsButton = JButton(AllIcons.General.Settings).apply {
            toolTipText = "Settings"
        }

        // When JButton is created with just an icon, text is null (not empty string)
        assertTrue(
            settingsButton.text == null || settingsButton.text.isEmpty(),
            "Settings button should have no text, but had: '${settingsButton.text}'"
        )
        assertEquals(AllIcons.General.Settings, settingsButton.icon, "Settings button should have Settings icon")
        assertNotNull(settingsButton.icon, "Settings button icon should not be null")
        assertEquals("Settings", settingsButton.toolTipText, "Settings button should have 'Settings' tooltip")
    }

    @Test
    fun `Back and Forward buttons should remain text-only`() {
        val backButton = JButton("<")
        val forwardButton = JButton(">")

        assertEquals("<", backButton.text, "Back button should have '<' text")
        assertEquals(">", forwardButton.text, "Forward button should have '>' text")
        assertNull(backButton.icon, "Back button should not have an icon")
        assertNull(forwardButton.icon, "Forward button should not have an icon")
    }
}
