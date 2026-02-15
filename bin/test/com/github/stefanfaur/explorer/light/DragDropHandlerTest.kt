package com.github.stefanfaur.explorer.light

import com.intellij.ide.dnd.DnDAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.actions.DragDropHandler
import com.github.stefanfaur.explorer.ui.FileTreeComponent
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Tests for the DragDropHandler which provides drag-and-drop support
 * for the FileTreeComponent's JTree.
 *
 * Since DnDEvent is an interface in the IntelliJ platform and cannot be
 * instantiated directly in tests, we test:
 * - canStartDragging / startDragging directly (they take DnDAction + Point)
 * - resolveTargetDirectory and performDrop as testable internal methods
 *
 * Note: FileTreeComponent.setRoot uses LocalFileSystem which does not
 * work with TempFileSystem in tests. We populate the tree model directly.
 */
class DragDropHandlerTest : BasePlatformTestCase() {

    private lateinit var testRoot: VirtualFile
    private lateinit var fileTreeComponent: FileTreeComponent
    private lateinit var handler: DragDropHandler

    override fun setUp() {
        super.setUp()
        testRoot = runWriteActionAndWait {
            myFixture.tempDirFixture.findOrCreateDir("dndTestRoot")
        }
        fileTreeComponent = FileTreeComponent(project)
        handler = DragDropHandler(fileTreeComponent, project)
    }

    override fun tearDown() {
        try {
            fileTreeComponent.dispose()
        } finally {
            super.tearDown()
        }
    }

    // ---- Instantiation ----

    fun `test handler can be instantiated with FileTreeComponent`() {
        assertNotNull(handler)
    }

    // ---- canStartDragging ----

    fun `test canStartDragging returns false when nothing is selected`() {
        fileTreeComponent.tree.clearSelection()

        val result = handler.canStartDragging(DnDAction.COPY, Point(10, 10))
        assertFalse("canStartDragging should return false when no files are selected", result)
    }

    fun `test canStartDragging returns true when files are selected`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "draggable.txt")
        }
        populateTree(listOf(file))
        selectFirstNode()

        val result = handler.canStartDragging(DnDAction.COPY, Point(10, 10))
        assertTrue("canStartDragging should return true when files are selected", result)
    }

    // ---- startDragging ----

    fun `test startDragging returns bean with Transferable attached object`() {
        val file = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "dragme.txt")
            f.setBinaryContent("drag content".toByteArray())
            f
        }
        populateTree(listOf(file))
        selectFirstNode()

        val bean = handler.startDragging(DnDAction.COPY, Point(10, 10))

        assertNotNull("startDragging should return a non-null bean", bean)
        val attached = bean!!.attachedObject
        assertNotNull("bean should have an attached object", attached)
        assertTrue("attached object should be a Transferable", attached is Transferable)

        val transferable = attached as Transferable
        assertTrue(
            "transferable should support javaFileListFlavor",
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        )

        @Suppress("UNCHECKED_CAST")
        val ioFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
        assertTrue("file list should contain at least one file", ioFiles.isNotEmpty())
        assertEquals("dragme.txt", ioFiles[0].name)
    }

    fun `test startDragging returns null when nothing selected`() {
        fileTreeComponent.tree.clearSelection()

        val bean = handler.startDragging(DnDAction.COPY, Point(10, 10))
        assertNull("startDragging should return null when nothing is selected", bean)
    }

    // ---- resolveTargetDirectory ----

    fun `test resolveTargetDirectory returns directory for directory node`() {
        val dir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "targetDir")
        }
        val node = DefaultMutableTreeNode(dir)

        val resolved = handler.resolveTargetDirectory(node)
        assertNotNull("resolveTargetDirectory should return a VirtualFile for a directory node", resolved)
        assertTrue("resolved target should be a directory", resolved!!.isDirectory)
        assertEquals("targetDir", resolved.name)
    }

    fun `test resolveTargetDirectory returns parent for file node`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "notADir.txt")
        }
        val node = DefaultMutableTreeNode(file)

        val resolved = handler.resolveTargetDirectory(node)
        assertNotNull("resolveTargetDirectory should return parent for file node", resolved)
        assertTrue("resolved target should be a directory", resolved!!.isDirectory)
        assertEquals(testRoot.name, resolved.name)
    }

    fun `test resolveTargetDirectory returns null for non-VirtualFile node`() {
        val node = DefaultMutableTreeNode("loading...")

        val resolved = handler.resolveTargetDirectory(node)
        assertNull("resolveTargetDirectory should return null for placeholder nodes", resolved)
    }

    // ---- performDrop (copy) ----

    fun `test performDrop copies files to target directory`() {
        val sourceFile = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "source.txt")
            f.setBinaryContent("copy me".toByteArray())
            f
        }
        val targetDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "dropTarget")
        }

        handler.performDrop(listOf(sourceFile), targetDir, isMove = false)

        targetDir.refresh(false, true)

        val copied = targetDir.findChild("source.txt")
        assertNotNull("File should be copied to the target directory", copied)
        assertEquals("copy me", String(copied!!.contentsToByteArray()))
        assertTrue("Original file should still exist after copy", sourceFile.exists())
    }

    // ---- performDrop with move ----

    fun `test performDrop with isMove true moves files instead of copying`() {
        val sourceFile = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "moveme.txt")
            f.setBinaryContent("move me".toByteArray())
            f
        }
        val targetDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "moveTarget")
        }

        handler.performDrop(listOf(sourceFile), targetDir, isMove = true)

        targetDir.refresh(false, true)
        testRoot.refresh(false, true)

        val moved = targetDir.findChild("moveme.txt")
        assertNotNull("File should be moved to the target directory", moved)
        assertEquals("move me", String(moved!!.contentsToByteArray()))
        assertNull("Original file should not exist after move", testRoot.findChild("moveme.txt"))
    }

    // ---- performDrop with multiple files ----

    fun `test performDrop copies multiple files`() {
        val file1 = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "file1.txt")
            f.setBinaryContent("content1".toByteArray())
            f
        }
        val file2 = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "file2.txt")
            f.setBinaryContent("content2".toByteArray())
            f
        }
        val targetDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "multiTarget")
        }

        handler.performDrop(listOf(file1, file2), targetDir, isMove = false)

        targetDir.refresh(false, true)

        assertNotNull("file1 should be copied", targetDir.findChild("file1.txt"))
        assertNotNull("file2 should be copied", targetDir.findChild("file2.txt"))
        assertTrue("Original file1 should still exist", file1.exists())
        assertTrue("Original file2 should still exist", file2.exists())
    }

    // ---- performDrop same-directory guard ----

    fun `test performDrop skips files already in the target directory`() {
        // Create a file directly inside testRoot
        val fileInRoot = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "already-here.txt")
            f.setBinaryContent("original content".toByteArray())
            f
        }

        // Count children before the drop
        testRoot.refresh(false, true)
        val childrenBefore = testRoot.children.map { it.name }.sorted()

        // Attempt to "copy" the file into its own parent directory (testRoot)
        handler.performDrop(listOf(fileInRoot), testRoot, isMove = false)

        testRoot.refresh(false, true)
        val childrenAfter = testRoot.children.map { it.name }.sorted()

        // The file should not be duplicated - directory contents should be unchanged
        assertEquals(
            "Directory contents should be unchanged when dropping a file onto its own parent",
            childrenBefore,
            childrenAfter
        )
        // Verify file content is untouched
        assertEquals("original content", String(fileInRoot.contentsToByteArray()))
    }

    fun `test performDrop skips files already in target but processes others`() {
        val fileInRoot = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "stay-put.txt")
            f.setBinaryContent("staying".toByteArray())
            f
        }
        val subDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "subDir")
        }
        val fileInSubDir = runWriteActionAndWait {
            val f = subDir.createChildData(this, "should-move.txt")
            f.setBinaryContent("moving".toByteArray())
            f
        }

        // Drop both files into testRoot: fileInRoot should be skipped, fileInSubDir should be copied
        handler.performDrop(listOf(fileInRoot, fileInSubDir), testRoot, isMove = false)

        testRoot.refresh(false, true)

        // fileInSubDir should now have a copy in testRoot
        val copied = testRoot.findChild("should-move.txt")
        assertNotNull("File from different directory should be copied to target", copied)
        assertEquals("moving", String(copied!!.contentsToByteArray()))

        // Original in subDir should still exist (copy, not move)
        subDir.refresh(false, true)
        assertNotNull("Original file should still exist in subDir", subDir.findChild("should-move.txt"))
    }

    // ---- createTransferable ----

    fun `test createTransferable produces valid file list transferable`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "transfer.txt")
        }

        val transferable = handler.createTransferable(listOf(file))

        assertTrue(
            "transferable should support javaFileListFlavor",
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        )

        @Suppress("UNCHECKED_CAST")
        val fileList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>
        assertEquals(1, fileList.size)
        assertEquals("transfer.txt", fileList[0].name)
    }

    fun `test createTransferable supports string flavor with paths`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "pathfile.txt")
        }

        val transferable = handler.createTransferable(listOf(file))

        assertTrue(
            "transferable should support stringFlavor",
            transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
        )

        val pathStr = transferable.getTransferData(DataFlavor.stringFlavor) as String
        assertTrue("path string should contain file path", pathStr.contains("pathfile.txt"))
    }

    // ---- Helper methods ----

    /**
     * Populates the tree model directly with the given VirtualFile list,
     * bypassing setRoot which requires LocalFileSystem.
     */
    private fun populateTree(files: List<VirtualFile>) {
        val model = fileTreeComponent.tree.model as DefaultTreeModel
        val root = model.root as DefaultMutableTreeNode
        root.removeAllChildren()
        for (file in files) {
            val node = DefaultMutableTreeNode(file)
            if (file.isDirectory) {
                // Add placeholder child so directory is expandable
                node.add(DefaultMutableTreeNode("loading..."))
            }
            root.add(node)
        }
        model.reload()
    }

    private fun selectFirstNode() {
        val root = fileTreeComponent.tree.model.root as DefaultMutableTreeNode
        if (root.childCount > 0) {
            val firstChild = root.getChildAt(0) as DefaultMutableTreeNode
            val path = TreePath(arrayOf(root, firstChild))
            fileTreeComponent.tree.selectionPath = path
        }
    }
}
