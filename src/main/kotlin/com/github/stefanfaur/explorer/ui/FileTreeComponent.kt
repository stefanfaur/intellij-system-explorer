package com.github.stefanfaur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.github.stefanfaur.explorer.model.FileTreeModel
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * Wraps a [Tree] (IntelliJ's JTree subclass) showing filesystem entries.
 *
 * Uses a simple [DefaultTreeModel] with [DefaultMutableTreeNode] approach.
 * Each node's userObject is the [VirtualFile] it represents (except for placeholder nodes).
 * Directories are lazily expanded via [TreeWillExpandListener].
 */
class FileTreeComponent(private val project: Project) {

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    val tree: JTree = Tree(treeModel)

    /** Called when a directory is double-clicked (navigate into it). */
    var onDirectoryDoubleClicked: ((VirtualFile) -> Unit)? = null

    /** Called when tree selection changes (for status bar updates, etc.). */
    var onSelectionChanged: (() -> Unit)? = null

    var showHidden: Boolean = false
    var filterPattern: String = ""

    private var currentRootPath: String? = null

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = VirtualFileCellRenderer()

        @Suppress("DEPRECATION")
        TreeSpeedSearch(tree) { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            vf?.name ?: node?.userObject?.toString() ?: ""
        }

        // Lazy directory expansion
        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val node = event.path.lastPathComponent as DefaultMutableTreeNode
                val file = node.userObject as? VirtualFile ?: return
                if (file.isDirectory && node.childCount == 1 &&
                    (node.firstChild as? DefaultMutableTreeNode)?.userObject is String
                ) {
                    // Replace placeholder with actual children
                    node.removeAllChildren()
                    loadChildren(node, file)
                    treeModel.nodeStructureChanged(node)
                }
            }

            override fun treeWillCollapse(event: TreeExpansionEvent) {}
        })

        // Double-click handler
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val vf = node.userObject as? VirtualFile ?: return
                    if (vf.isDirectory) {
                        onDirectoryDoubleClicked?.invoke(vf)
                    } else {
                        FileEditorManager.getInstance(project).openFile(vf, true)
                    }
                }
            }
        })

        // Selection change listener
        tree.addTreeSelectionListener {
            onSelectionChanged?.invoke()
        }
    }

    /**
     * Sets the root directory for the tree and populates the first level of children.
     */
    fun setRoot(path: String) {
        currentRootPath = path
        rootNode.removeAllChildren()
        val dir = LocalFileSystem.getInstance().findFileByPath(path)
        if (dir != null && dir.isDirectory) {
            loadChildren(rootNode, dir)
        }
        treeModel.reload()
    }

    /**
     * Refreshes the tree by re-reading the current root.
     */
    fun refresh() {
        currentRootPath?.let { setRoot(it) }
    }

    /**
     * Returns all VirtualFile objects currently visible as top-level children of the root.
     */
    fun getRootChildren(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode
            val vf = child?.userObject as? VirtualFile
            if (vf != null) result.add(vf)
        }
        return result
    }

    /**
     * Returns the currently selected VirtualFile objects in the tree.
     */
    fun getSelectedFiles(): List<VirtualFile> {
        val paths = tree.selectionPaths ?: return emptyList()
        return paths.mapNotNull { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            node?.userObject as? VirtualFile
        }
    }

    private fun loadChildren(parentNode: DefaultMutableTreeNode, parentFile: VirtualFile) {
        val model = FileTreeModel(showHidden = showHidden, foldersFirst = true)
        val children = if (filterPattern.isNotBlank()) {
            model.getFilteredChildren(parentFile, filterPattern)
        } else {
            model.getChildren(parentFile)
        }

        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            if (child.isDirectory) {
                // Add a placeholder child so the node is expandable
                childNode.add(DefaultMutableTreeNode("loading..."))
            }
            parentNode.add(childNode)
        }
    }

    /**
     * Custom cell renderer that displays VirtualFile names with appropriate icons.
     */
    private inner class VirtualFileCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            val vf = node?.userObject as? VirtualFile
            if (vf != null) {
                text = vf.name
                icon = getIconForFile(vf, expanded)
            }
            return component
        }

        private fun getIconForFile(vf: VirtualFile, expanded: Boolean): Icon {
            return if (vf.isDirectory) {
                if (expanded) AllIcons.Nodes.Folder else AllIcons.Nodes.Folder
            } else {
                vf.fileType.icon ?: AllIcons.FileTypes.Any_type
            }
        }
    }
}
