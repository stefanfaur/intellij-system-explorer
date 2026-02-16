package ro.faur.explorer.light

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.model.FileTreeModel
import ro.faur.explorer.util.FileSizeFormatter

class FileTreeModelTest : BasePlatformTestCase() {

    private lateinit var testRoot: VirtualFile

    override fun setUp() {
        super.setUp()
        testRoot = runWriteActionAndWait {
            val root = myFixture.tempDirFixture.findOrCreateDir("treeRoot")
            // Create test structure
            root.createChildDirectory(this, "folderA")
            root.createChildDirectory(this, "folderB")
            root.createChildDirectory(this, ".hiddenDir")
            root.createChildData(this, "file1.txt")
            root.createChildData(this, "file2.kt")
            root.createChildData(this, ".dotfile")
            root
        }
    }

    fun `test getChildren returns all non-hidden entries by default`() {
        val model = FileTreeModel(showHidden = false, foldersFirst = true)
        val children = model.getChildren(testRoot)

        // Should exclude .hiddenDir and .dotfile
        val names = children.map { it.name }
        assertTrue("folderA" in names)
        assertTrue("folderB" in names)
        assertTrue("file1.txt" in names)
        assertTrue("file2.kt" in names)
        assertFalse(".hiddenDir" in names)
        assertFalse(".dotfile" in names)
    }

    fun `test getChildren includes hidden when showHidden is true`() {
        val model = FileTreeModel(showHidden = true, foldersFirst = true)
        val children = model.getChildren(testRoot)

        val names = children.map { it.name }
        assertTrue(".hiddenDir" in names)
        assertTrue(".dotfile" in names)
    }

    fun `test getChildren sorts folders first when enabled`() {
        val model = FileTreeModel(showHidden = false, foldersFirst = true)
        val children = model.getChildren(testRoot)

        // First entries should be directories
        assertTrue(children[0].isDirectory)
        assertTrue(children[1].isDirectory)
        assertFalse(children[2].isDirectory)
        assertFalse(children[3].isDirectory)
    }

    fun `test getChildren with filter returns only matching files`() {
        val model = FileTreeModel(showHidden = false, foldersFirst = true)
        val filtered = model.getFilteredChildren(testRoot, "*.kt")

        val names = filtered.map { it.name }
        // Directories always pass filter (for tree navigation)
        assertTrue("folderA" in names)
        assertTrue("folderB" in names)
        // Only .kt files from files
        assertTrue("file2.kt" in names)
        assertFalse("file1.txt" in names)
    }

    fun `test isLeaf returns true for files`() {
        val model = FileTreeModel(showHidden = false, foldersFirst = true)
        val file = testRoot.findChild("file1.txt")!!

        assertTrue(model.isLeaf(file))
    }

    fun `test isLeaf returns false for directories`() {
        val model = FileTreeModel(showHidden = false, foldersFirst = true)
        val dir = testRoot.findChild("folderA")!!

        assertFalse(model.isLeaf(dir))
    }

    fun `test directory stats count children correctly`() {
        val dir = runWriteActionAndWait {
            val d = myFixture.tempDirFixture.findOrCreateDir("statsDir")
            d.createChildData(this, "file1.txt").setBinaryContent("abc".toByteArray())
            d.createChildData(this, "file2.txt").setBinaryContent("defgh".toByteArray())
            d.createChildDirectory(this, "subdir")
            d
        }
        assertEquals(3, FileSizeFormatter.countDirectChildren(dir))
    }

    fun `test directory stats compute immediate size correctly`() {
        val dir = runWriteActionAndWait {
            val d = myFixture.tempDirFixture.findOrCreateDir("sizeDir")
            d.createChildData(this, "a.txt").setBinaryContent("abc".toByteArray())    // 3 bytes
            d.createChildData(this, "b.txt").setBinaryContent("defgh".toByteArray())  // 5 bytes
            d.createChildDirectory(this, "subdir")  // 0 bytes
            d
        }
        assertEquals(8L, FileSizeFormatter.computeDirectoryImmediateSize(dir))
    }
}
