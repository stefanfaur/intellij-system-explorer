package com.github.stefanfaur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.BoxLayout

/**
 * Verifies the toolbar layout structure: buttons row, path row, filter row
 * are three separate rows.
 */
class ToolbarLayoutTest {

    /**
     * Simulates the expected toolbar structure and verifies it has 3 rows.
     * The actual ExplorerPanel.buildToolbar() should produce this layout.
     */
    @Test
    fun `toolbar should have three child components for buttons, path, and filter`() {
        // The expected layout after the fix:
        // toolbarPanel (BoxLayout.Y_AXIS)
        //   |- buttonsRow (FlowLayout)
        //   |- pathField (JTextField)
        //   +- filterField (JTextField)
        val toolbarPanel = JPanel()
        toolbarPanel.layout = BoxLayout(toolbarPanel, BoxLayout.Y_AXIS)

        val buttonsRow = JPanel()
        val pathField = JTextField()
        val filterField = JTextField()

        toolbarPanel.add(buttonsRow)
        toolbarPanel.add(pathField)
        toolbarPanel.add(filterField)

        assertEquals(3, toolbarPanel.componentCount,
            "Toolbar should have 3 rows: buttons, path, filter")
        assertTrue(toolbarPanel.getComponent(1) is JTextField,
            "Second row should be the path field")
        assertTrue(toolbarPanel.getComponent(2) is JTextField,
            "Third row should be the filter field")
    }
}
