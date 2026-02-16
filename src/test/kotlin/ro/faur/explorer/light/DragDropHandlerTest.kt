package ro.faur.explorer.light

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.actions.DragDropHandler
import ro.faur.explorer.ui.FileTreeComponent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Tests for the DragDropHandler which provides DnDTarget support
 * for the FileTreeComponent's JTree (receiving drops from IntelliJ panels).
 *
 * Drag-out is handled by Swing's TransferHandler (FileTreeTransferHandler),
 * tested via the TransferHandler tests below.
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
        val fileInRoot = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "already-here.txt")
            f.setBinaryContent("original content".toByteArray())
            f
        }

        testRoot.refresh(false, true)
        val childrenBefore = testRoot.children.map { it.name }.sorted()

        handler.performDrop(listOf(fileInRoot), testRoot, isMove = false)

        testRoot.refresh(false, true)
        val childrenAfter = testRoot.children.map { it.name }.sorted()

        assertEquals(
            "Directory contents should be unchanged when dropping a file onto its own parent",
            childrenBefore,
            childrenAfter
        )
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

        handler.performDrop(listOf(fileInRoot, fileInSubDir), testRoot, isMove = false)

        testRoot.refresh(false, true)

        val copied = testRoot.findChild("should-move.txt")
        assertNotNull("File from different directory should be copied to target", copied)
        assertEquals("moving", String(copied!!.contentsToByteArray()))

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

    // ---- Swing DnD configuration ----

    fun `test tree dragEnabled should be true for Swing drag-out`() {
        assertTrue(
            "tree.dragEnabled must be true so Swing's TransferHandler can initiate drags to other panels",
            fileTreeComponent.tree.dragEnabled
        )
    }

    fun `test tree has a TransferHandler set`() {
        assertNotNull(
            "Tree should have a TransferHandler for drag-out and paste support",
            fileTreeComponent.tree.transferHandler
        )
    }

    fun `test TransferHandler canImport for file list flavor`() {
        val handler = fileTreeComponent.tree.transferHandler
        assertNotNull(handler)

        val support = handler.canImport(
            javax.swing.TransferHandler.TransferSupport(
                fileTreeComponent.tree,
                createTestTransferable(listOf(testRoot))
            )
        )
        assertTrue("TransferHandler should support importing file lists", support)
    }

    private fun createTestTransferable(files: List<VirtualFile>): Transferable {
        val ioFiles = files.map { java.io.File(it.path) }
        return object : Transferable {
            private val flavors = arrayOf(DataFlavor.javaFileListFlavor)
            override fun getTransferDataFlavors() = flavors
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in flavors
            override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                DataFlavor.javaFileListFlavor -> ioFiles
                else -> throw UnsupportedFlavorException(flavor)
            }
        }
    }

    // ---- Helper methods ----

    private fun populateTree(files: List<VirtualFile>) {
        val model = fileTreeComponent.tree.model as DefaultTreeModel
        val root = model.root as DefaultMutableTreeNode
        root.removeAllChildren()
        for (file in files) {
            val node = DefaultMutableTreeNode(file)
            if (file.isDirectory) {
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
