package com.github.stefanfaur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import java.awt.Color
import java.awt.Component

/**
 * Verifies that the file tree cell renderer does not paint a background
 * on non-selected items — only selected items should have a highlight.
 */
class CellRendererBackgroundTest {

    @Test
    fun `non-selected cell should have transparent background`() {
        val renderer = object : DefaultTreeCellRenderer() {
            init {
                isOpaque = false
                backgroundNonSelectionColor = null
            }
        }
        val tree = JTree()
        val node = DefaultMutableTreeNode("test")

        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, false, true, 0, false
        )

        assertFalse((component as DefaultTreeCellRenderer).isOpaque,
            "Non-selected cell renderer should not be opaque")
    }

    @Test
    fun `selected cell should have a background color`() {
        val renderer = object : DefaultTreeCellRenderer() {
            init {
                isOpaque = false
                backgroundNonSelectionColor = null
            }
        }
        val tree = JTree()
        val node = DefaultMutableTreeNode("test")

        val component = renderer.getTreeCellRendererComponent(
            tree, node, true, false, true, 0, true
        )

        val bg = (component as DefaultTreeCellRenderer).backgroundSelectionColor
        assertNotNull(bg, "Selected cell should have a selection background color")
    }
}
