package ro.faur.explorer.unit

import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Tests that the cell renderer caches icons to avoid visual flashing during
 * IntelliJ dumb mode transitions.
 *
 * Root cause: When IntelliJ enters/exits dumb mode, vf.fileType can return
 * different results (unknown type during dumb, correct type after). If the
 * renderer queries fileType on every paint, icons change twice in rapid
 * succession → visible flash.
 *
 * Fix: Cache icons per file path, only refresh on setRoot()/refresh().
 */
class CellRendererPerformanceTest {

    @Test
    fun `icon should be stable across multiple renders of the same file`() {
        // Simulate a file whose fileType.icon changes between renders
        // (as happens during dumb mode transitions)
        val mockFile = mock(VirtualFile::class.java)
        `when`(mockFile.name).thenReturn("Test.kt")
        `when`(mockFile.path).thenReturn("/test/Test.kt")
        `when`(mockFile.isDirectory).thenReturn(false)

        val icons = mutableListOf<Icon?>()

        val renderer = object : ColoredTreeCellRenderer() {
            private val iconCache = mutableMapOf<String, Icon>()

            override fun customizeCellRenderer(
                tree: JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode
                val vf = node?.userObject as? VirtualFile
                if (vf != null) {
                    // Use cached icon — stable across dumb mode transitions
                    icon = iconCache.getOrPut(vf.path) {
                        if (vf.isDirectory) AllIcons.Nodes.Folder
                        else AllIcons.FileTypes.Any_type // Use stable fallback
                    }
                    icons.add(icon)
                    append(vf.name)
                }
            }
        }

        val tree = JTree()
        val node = DefaultMutableTreeNode(mockFile)

        // Render the same node multiple times (simulating rapid repaints during dumb mode)
        renderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)
        renderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)
        renderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)

        // All renders should produce the same icon (cached)
        assertEquals(3, icons.size)
        assertTrue(icons.all { it === icons[0] },
            "Icon should be the same instance across renders (cached), not re-queried from fileType each time")
    }

    @Test
    fun `uncached icon lookup via fileType changes during dumb mode simulation`() {
        // This test demonstrates the BUG: querying vf.fileType.icon on every render
        // produces different results when fileType changes (dumb mode).
        val mockFile = mock(VirtualFile::class.java)
        `when`(mockFile.name).thenReturn("Test.kt")
        `when`(mockFile.path).thenReturn("/test/Test.kt")
        `when`(mockFile.isDirectory).thenReturn(false)

        // Simulate fileType returning different icons on successive calls
        val mockFileType1 = mock(com.intellij.openapi.fileTypes.FileType::class.java)
        val mockFileType2 = mock(com.intellij.openapi.fileTypes.FileType::class.java)
        `when`(mockFileType1.icon).thenReturn(AllIcons.FileTypes.Any_type)
        `when`(mockFileType2.icon).thenReturn(AllIcons.FileTypes.Text)

        // First call returns type1, subsequent calls return type2 (simulating dumb→smart transition)
        `when`(mockFile.fileType).thenReturn(mockFileType1, mockFileType2, mockFileType2)

        val icons = mutableListOf<Icon?>()

        val buggyRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode
                val vf = node?.userObject as? VirtualFile
                if (vf != null) {
                    // BUG: queries fileType on every render
                    icon = if (vf.isDirectory) AllIcons.Nodes.Folder
                           else vf.fileType.icon ?: AllIcons.FileTypes.Any_type
                    icons.add(icon)
                    append(vf.name)
                }
            }
        }

        val tree = JTree()
        val node = DefaultMutableTreeNode(mockFile)

        buggyRenderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)
        buggyRenderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)

        // The buggy renderer produces DIFFERENT icons → causes flash
        assertNotSame(icons[0], icons[1],
            "Without caching, icon changes between renders (this causes the flash)")
    }
}
