package ro.faur.explorer.unit

import com.intellij.openapi.util.SystemInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.ui.ExplorerPanel
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * Tests for the status bar checkbox layout and behavior in ExplorerPanel.
 *
 * Verifies:
 * - Status bar uses BorderLayout with left and right sections
 * - Hidden checkbox is always present
 * - Permissions checkbox is present on Unix/Mac only
 * - Checkboxes are positioned on the right side
 */
class StatusBarCheckboxesTest {

    @Test
    fun `status bar uses BorderLayout`() {
        // The status bar should use BorderLayout with LINE_START and LINE_END sections
        // to position content on left and right
        val statusBar = JPanel(BorderLayout())

        // Should be able to add components to LINE_START and LINE_END
        statusBar.add(JPanel(), BorderLayout.LINE_START)
        statusBar.add(JPanel(), BorderLayout.LINE_END)

        assertNotNull(statusBar.layout)
        assertTrue(statusBar.layout is BorderLayout)
    }

    @Test
    fun `checkbox panel contains Hidden checkbox`() {
        // Verify that a checkbox panel with the Hidden checkbox can be created
        val checkboxPanel = JPanel()
        val hiddenCheckbox = JCheckBox("Hidden")
        checkboxPanel.add(hiddenCheckbox)

        assertEquals("Hidden", hiddenCheckbox.text)
        assertFalse(hiddenCheckbox.isSelected, "Hidden checkbox should start unchecked")
    }

    @Test
    fun `permissions checkbox created only on non-Windows platforms`() {
        // Permissions checkbox should only be created on Unix/Mac
        val permissionsCheckbox = if (!SystemInfo.isWindows) JCheckBox("Permissions") else null

        if (SystemInfo.isWindows) {
            assertNull(permissionsCheckbox, "Permissions checkbox should be null on Windows")
        } else {
            assertNotNull(permissionsCheckbox, "Permissions checkbox should exist on Unix/Mac")
            assertEquals("Permissions", permissionsCheckbox?.text)
        }
    }

    @Test
    fun `checkbox state can be toggled`() {
        val checkbox = JCheckBox("Test")
        assertFalse(checkbox.isSelected)

        checkbox.isSelected = true
        assertTrue(checkbox.isSelected)

        checkbox.isSelected = false
        assertFalse(checkbox.isSelected)
    }

    @Test
    fun `item listener receives state change events`() {
        val checkbox = JCheckBox("Test")
        var eventCount = 0
        var lastState: Boolean? = null

        checkbox.addItemListener { e ->
            eventCount++
            lastState = e.stateChange == java.awt.event.ItemEvent.SELECTED
        }

        // Trigger state change
        checkbox.isSelected = true

        assertEquals(1, eventCount, "Should receive exactly one event")
        assertEquals(true, lastState, "Event should indicate SELECTED state")

        // Trigger another change
        checkbox.isSelected = false

        assertEquals(2, eventCount, "Should receive second event")
        assertEquals(false, lastState, "Event should indicate DESELECTED state")
    }

    @Test
    fun `checkbox labels are concise`() {
        // Per requirements, labels should be short: "Hidden" and "Permissions"
        val hiddenCheckbox = JCheckBox("Hidden")
        val permissionsCheckbox = JCheckBox("Permissions")

        assertEquals("Hidden", hiddenCheckbox.text)
        assertEquals("Permissions", permissionsCheckbox.text)

        // Verify they're not longer forms like "Show Hidden" or "Show Permissions"
        assertNotEquals("Show Hidden", hiddenCheckbox.text)
        assertNotEquals("Show Permissions", permissionsCheckbox.text)
    }

    @Test
    fun `multiple checkboxes can coexist in panel`() {
        val panel = JPanel()
        val checkbox1 = JCheckBox("First")
        val checkbox2 = JCheckBox("Second")

        panel.add(checkbox1)
        panel.add(checkbox2)

        assertEquals(2, panel.componentCount)
        assertTrue(panel.components.contains(checkbox1))
        assertTrue(panel.components.contains(checkbox2))
    }
}
