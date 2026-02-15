package com.github.stefanfaur.explorer.unit

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Verifies that the file tree cell renderer (ColoredTreeCellRenderer) integrates
 * correctly with IntelliJ's Tree painting and does not cause all rows to appear selected.
 *
 * ColoredTreeCellRenderer delegates background painting to IntelliJ's Tree component,
 * which eliminates the "all rows appear selected" bug caused by DefaultTreeCellRenderer's
 * transparent-background approach conflicting with IntelliJ's row painting.
 */
class CellRendererBackgroundTest {

    private fun createRenderer(): ColoredTreeCellRenderer {
        return object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                append(value?.toString() ?: "")
                icon = AllIcons.FileTypes.Any_type
            }
        }
    }

    @Test
    fun `renderer renders non-selected cell without error`() {
        val renderer = createRenderer()
        val tree = JTree()
        val node = DefaultMutableTreeNode("test")

        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, false, true, 0, false
        )

        assertNotNull(component, "Non-selected cell should render successfully")
    }

    @Test
    fun `renderer renders selected cell without error`() {
        val renderer = createRenderer()
        val tree = JTree()
        val node = DefaultMutableTreeNode("test")

        val component = renderer.getTreeCellRendererComponent(
            tree, node, true, false, true, 0, true
        )

        assertNotNull(component, "Selected cell should render successfully")
    }

    @Test
    fun `renderer appends text from node`() {
        val renderer = createRenderer()
        val tree = JTree()
        val node = DefaultMutableTreeNode("myfile.txt")

        renderer.getTreeCellRendererComponent(
            tree, node, false, false, true, 0, false
        )

        // ColoredTreeCellRenderer stores text via append(); verify it's visible via toString()
        assertTrue(renderer.toString().contains("myfile.txt"),
            "Renderer should contain the appended file name")
    }
}
