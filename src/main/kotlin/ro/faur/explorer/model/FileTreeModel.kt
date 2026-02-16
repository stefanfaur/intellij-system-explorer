package ro.faur.explorer.model

import com.intellij.openapi.vfs.VirtualFile
import ro.faur.explorer.util.GlobFilter

/**
 * Provides sorted, filtered access to VFS children for the file tree.
 *
 * Wraps [VirtualFile.getChildren] with:
 * - Hidden file filtering (files/dirs starting with '.')
 * - Folders-first sorting with case-insensitive alphabetical order
 * - Glob pattern filtering (directories always pass for tree navigation)
 */
class FileTreeModel(
    val showHidden: Boolean,
    val foldersFirst: Boolean,
) {

    /**
     * Returns the children of [parent], filtered and sorted according to the model's settings.
     * Hidden files (names starting with '.') are excluded unless [showHidden] is true.
     * When [foldersFirst] is true, directories appear before files, with each group
     * sorted case-insensitively by name.
     */
    fun getChildren(parent: VirtualFile): List<VirtualFile> {
        val children = parent.children?.toList() ?: emptyList()
        return children
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(virtualFileComparator())
    }

    /**
     * Returns the children of [parent] filtered by the given glob [pattern].
     * Directories always pass the filter to allow tree navigation.
     * Files must match the glob pattern to be included.
     * Hidden file filtering and sorting are still applied.
     */
    fun getFilteredChildren(parent: VirtualFile, pattern: String): List<VirtualFile> {
        val globFilter = GlobFilter(pattern)
        return getChildren(parent).filter { it.isDirectory || globFilter.matches(it.name) }
    }

    /**
     * Returns true if the given [file] is a leaf node (i.e., not a directory).
     */
    fun isLeaf(file: VirtualFile): Boolean = !file.isDirectory

    private fun virtualFileComparator(): Comparator<VirtualFile> = Comparator { a, b ->
        if (foldersFirst) {
            if (a.isDirectory && !b.isDirectory) return@Comparator -1
            if (!a.isDirectory && b.isDirectory) return@Comparator 1
        }
        a.name.compareTo(b.name, ignoreCase = true)
    }
}
