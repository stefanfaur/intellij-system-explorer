package ro.faur.explorer.remote

import com.intellij.openapi.application.ApplicationManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Tree model for the remote SFTP file browser.
 *
 * Encapsulates the [DefaultTreeModel], directory listing via [SftpFileOperations],
 * sorting (directories first, then case-insensitive name), and [DirectoryCache] integration.
 *
 * All I/O runs on a background daemon thread; UI updates are dispatched to the EDT.
 */
class SftpFileTreeModel(
    private val cache: DirectoryCache,
) {
    companion object {
        /** Placeholder userObject inserted into unexpanded directory nodes. */
        const val LOADING_PLACEHOLDER = "loading..."

        /** Placeholder inserted when directory listing fails (e.g. EACCES). */
        const val ACCESS_DENIED_PLACEHOLDER = "Permission denied"
    }

    private val root = DefaultMutableTreeNode("(not connected)")
    val treeModel: DefaultTreeModel = DefaultTreeModel(root)

    /**
     * Resets the tree to the "(not connected)" placeholder.
     */
    fun showDisconnected() {
        val newRoot = DefaultMutableTreeNode("(not connected)")
        treeModel.setRoot(newRoot)
        treeModel.reload()
    }

    /**
     * Loads directory contents for [path] using [fileOps], replacing the tree root on the EDT.
     * Directory nodes are given a [LOADING_PLACEHOLDER] child so the expand arrow is shown.
     *
     * @param connKey   The connection name (used as cache key).
     * @param path      The remote directory path to list.
     * @param fileOps   The SFTP operations handle for listing.
     * @param filter    Optional function applied to raw entries before building tree nodes.
     *                  Receives all entries; returns the subset to display. Callers can
     *                  perform side-effects (e.g. update status bar) inside this lambda.
     * @param onLoaded  Optional callback invoked on the I/O thread with the raw entries
     *                  (before filtering/sorting), for callers that need to inspect them
     *                  (e.g. git root detection).
     * @param onError   Optional callback invoked on the I/O thread when listing fails.
     */
    fun loadDirectory(
        connKey: String,
        path: String,
        fileOps: SftpFileOperations,
        filter: ((List<SftpEntry>) -> List<SftpEntry>)? = null,
        onLoaded: ((List<SftpEntry>) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null,
    ) {
        Thread {
            try {
                val entries: List<SftpEntry> = cache.get(connKey, path)
                    ?: fileOps.listDirectory(path, includeHidden = true).also { cache.put(connKey, path, it) }

                onLoaded?.invoke(entries)

                val toDisplay = filter?.invoke(entries) ?: entries
                val sorted = toDisplay.sortedWith(
                    compareBy<SftpEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )

                ApplicationManager.getApplication().invokeLater {
                    val newRoot = DefaultMutableTreeNode(path)
                    sorted.forEach { entry -> newRoot.add(makeNode(entry)) }
                    treeModel.setRoot(newRoot)
                    treeModel.reload()
                }
            } catch (e: Exception) {
                onError?.invoke(e)
                ApplicationManager.getApplication().invokeLater {
                    val errorRoot = DefaultMutableTreeNode("Error: ${e.message}")
                    treeModel.setRoot(errorRoot)
                    treeModel.reload()
                }
            }
        }.also { it.isDaemon = true; it.name = "RemoteTree[$path]" }.start()
    }

    /**
     * Lazily loads children of an already-visible directory node.
     *
     * Intended for use by a [javax.swing.event.TreeWillExpandListener]: called when the
     * user clicks the expand arrow on a directory node that still has the
     * [LOADING_PLACEHOLDER] child.
     *
     * Guards against concurrent expansions: if the node has already been populated
     * (childCount != 1 or child is not the placeholder) the update is skipped.
     */
    fun loadChildren(
        connKey: String,
        parentNode: DefaultMutableTreeNode,
        path: String,
        fileOps: SftpFileOperations,
        filter: ((List<SftpEntry>) -> List<SftpEntry>)? = null,
        onLoaded: ((List<SftpEntry>) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null,
    ) {
        Thread {
            try {
                val entries: List<SftpEntry> = cache.get(connKey, path)
                    ?: fileOps.listDirectory(path, includeHidden = true).also { cache.put(connKey, path, it) }

                onLoaded?.invoke(entries)

                val toDisplay = filter?.invoke(entries) ?: entries
                val sorted = toDisplay.sortedWith(
                    compareBy<SftpEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )

                ApplicationManager.getApplication().invokeLater {
                    // Skip if another expansion already populated this node
                    val isStillPlaceholder = parentNode.childCount == 1 &&
                            (parentNode.firstChild as? DefaultMutableTreeNode)?.userObject == LOADING_PLACEHOLDER
                    if (!isStillPlaceholder) return@invokeLater

                    parentNode.removeAllChildren()
                    sorted.forEach { entry -> parentNode.add(makeNode(entry)) }
                    treeModel.nodeStructureChanged(parentNode)
                }
            } catch (e: Exception) {
                onError?.invoke(e)
                ApplicationManager.getApplication().invokeLater {
                    val isStillPlaceholder = parentNode.childCount == 1 &&
                            (parentNode.firstChild as? DefaultMutableTreeNode)?.userObject == LOADING_PLACEHOLDER
                    if (!isStillPlaceholder) return@invokeLater
                    parentNode.removeAllChildren()
                    parentNode.add(DefaultMutableTreeNode(ACCESS_DENIED_PLACEHOLDER))
                    treeModel.nodeStructureChanged(parentNode)
                }
            }
        }.also { it.isDaemon = true; it.name = "RemoteExpand[$path]" }.start()
    }

    /** Creates a tree node for [entry], adding a placeholder child if it is a directory. */
    private fun makeNode(entry: SftpEntry): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(entry)
        if (entry.isDirectory) node.add(DefaultMutableTreeNode(LOADING_PLACEHOLDER))
        return node
    }
}
