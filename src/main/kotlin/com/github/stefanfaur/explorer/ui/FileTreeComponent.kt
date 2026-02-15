package com.github.stefanfaur.explorer.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import java.io.File
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Wraps a [Tree] (IntelliJ's JTree subclass) showing filesystem entries.
 *
 * Uses a simple [DefaultTreeModel] with [DefaultMutableTreeNode] approach.
 * Each node's userObject is the [File] it represents.
 * Only one level of children is loaded at a time (lazy expansion can be added later).
 */
class FileTreeComponent(private val project: Project) {

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    val tree: JTree = Tree(treeModel)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        @Suppress("DEPRECATION")
        TreeSpeedSearch(tree)
    }

    /**
     * Sets the root directory for the tree and populates the first level of children.
     */
    fun setRoot(path: String) {
        rootNode.removeAllChildren()
        val dir = File(path)
        if (dir.isDirectory) {
            val children = dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyArray<File>().toList()
            for (child in children) {
                val childNode = DefaultMutableTreeNode(child.name)
                if (child.isDirectory) {
                    // Add a dummy child so the node is expandable
                    childNode.add(DefaultMutableTreeNode("loading..."))
                }
                rootNode.add(childNode)
            }
        }
        treeModel.reload()
    }

    /**
     * Refreshes the tree by re-reading the current root.
     */
    fun refresh() {
        val root = rootNode.userObject
        if (root is String && root != "root") {
            setRoot(root)
        }
    }
}
