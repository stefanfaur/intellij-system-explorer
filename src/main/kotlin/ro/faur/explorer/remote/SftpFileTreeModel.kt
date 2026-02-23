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
     * Loads directory contents for [path] using [fileOps], updating the tree model on the EDT.
     *
     * @param connKey   The connection name (used as cache key).
     * @param path      The remote directory path to list.
     * @param fileOps   The SFTP operations handle for listing.
     * @param onLoaded  Optional callback invoked on the I/O thread with the raw entries
     *                  (before sorting), for callers that need to inspect them (e.g. git root detection).
     * @param onError   Optional callback invoked on the I/O thread when listing fails.
     */
    fun loadDirectory(
        connKey: String,
        path: String,
        fileOps: SftpFileOperations,
        onLoaded: ((List<SftpEntry>) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null,
    ) {
        Thread {
            try {
                val entries: List<SftpEntry> = cache.get(connKey, path)
                    ?: fileOps.listDirectory(path, includeHidden = true).also { cache.put(connKey, path, it) }

                onLoaded?.invoke(entries)

                val sorted = entries.sortedWith(
                    compareBy<SftpEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )

                ApplicationManager.getApplication().invokeLater {
                    val newRoot = DefaultMutableTreeNode(path)
                    sorted.forEach { entry -> newRoot.add(DefaultMutableTreeNode(entry)) }
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
}
