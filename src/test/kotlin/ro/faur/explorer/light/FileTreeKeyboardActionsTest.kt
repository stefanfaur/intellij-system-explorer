package ro.faur.explorer.light

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ui.FileTreeComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FileTreeKeyboardActionsTest : BasePlatformTestCase() {

    private lateinit var rootDir: VirtualFile
    private lateinit var fileDir: VirtualFile
    private lateinit var file: VirtualFile
    private lateinit var fileTreeComponent: FileTreeComponent

    override fun setUp() {
        super.setUp()
        rootDir = runWriteActionAndWait {
            myFixture.tempDirFixture.findOrCreateDir("keyboardOpenRoot")
        }
        fileDir = runWriteActionAndWait {
            rootDir.createChildDirectory(this, "nestedDir")
        }
        file = runWriteActionAndWait {
            val created = rootDir.createChildData(this, "open-me.txt")
            created.setBinaryContent("hello".toByteArray())
            created
        }

        fileTreeComponent = FileTreeComponent(project)
        populateRootChildren(rootDir.children.toList())
    }

    override fun tearDown() {
        try {
            fileTreeComponent.dispose()
        } finally {
            super.tearDown()
        }
    }

    fun `test openSelected opens file in editor`() {
        selectSingleNode("open-me.txt")

        fileTreeComponent.openSelected()

        val opened = FileEditorManager.getInstance(project).selectedFiles
        assertTrue("Selected file should be opened in editor", opened.contains(file))
    }

    fun `test openSelected navigates into directory via callback`() {
        var navigatedPath: String? = null
        fileTreeComponent.onDirectoryDoubleClicked = { navigatedPath = it.path }

        selectSingleNode("nestedDir")
        fileTreeComponent.openSelected()

        assertEquals(fileDir.path, navigatedPath)
    }

    fun `test openSelected does nothing for multi-selection`() {
        var callbackFired = false
        fileTreeComponent.onDirectoryDoubleClicked = { callbackFired = true }

        val rootNode = (fileTreeComponent.tree.model as DefaultTreeModel).root as DefaultMutableTreeNode
        val dirNode = findChildNode(rootNode, "nestedDir")
        val fileNode = findChildNode(rootNode, "open-me.txt")
        fileTreeComponent.tree.selectionPaths = arrayOf(
            TreePath(arrayOf(rootNode, dirNode)),
            TreePath(arrayOf(rootNode, fileNode))
        )

        fileTreeComponent.openSelected()

        assertFalse("Directory callback should not fire for multi-selection", callbackFired)
        assertFalse(
            "No file should be opened for multi-selection",
            FileEditorManager.getInstance(project).selectedFiles.contains(file)
        )
    }

    private fun populateRootChildren(children: List<VirtualFile>) {
        val model = fileTreeComponent.tree.model as DefaultTreeModel
        val rootNode = model.root as DefaultMutableTreeNode
        rootNode.removeAllChildren()

        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            if (child.isDirectory) {
                childNode.add(DefaultMutableTreeNode("loading..."))
            } else {
                childNode.allowsChildren = false
            }
            rootNode.add(childNode)
        }

        model.reload()
    }

    private fun selectSingleNode(name: String) {
        val model = fileTreeComponent.tree.model as DefaultTreeModel
        val rootNode = model.root as DefaultMutableTreeNode
        val node = findChildNode(rootNode, name)
        fileTreeComponent.tree.selectionPath = TreePath(arrayOf(rootNode, node))
    }

    private fun findChildNode(rootNode: DefaultMutableTreeNode, name: String): DefaultMutableTreeNode {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val vf = child.userObject as? VirtualFile ?: continue
            if (vf.name == name) return child
        }
        throw AssertionError("Could not find tree node with name '$name'")
    }
}
