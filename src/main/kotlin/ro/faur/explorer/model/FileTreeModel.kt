package ro.faur.explorer.model

import com.intellij.openapi.application.ApplicationManager
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

    private val fileComparator = FileComparator(foldersFirst)
    private val vfComparator = Comparator<VirtualFile> { a, b ->
        fileComparator.compare(FileEntry(a.name, a.isDirectory), FileEntry(b.name, b.isDirectory))
    }

    @Volatile private var cachedGlobFilter: GlobFilter? = null
    @Volatile private var cachedGlobPattern: String = ""

    /**
     * Returns the children of [parent], filtered and sorted according to the model's settings.
     * Hidden files (names starting with '.') are excluded unless [showHidden] is true.
     * When [foldersFirst] is true, directories appear before files, with each group
     * sorted case-insensitively by name.
     */
    fun getChildren(parent: VirtualFile): List<VirtualFile> {
        val children = ApplicationManager.getApplication().runReadAction<Array<VirtualFile>?> {
            parent.children
        }?.toList() ?: emptyList()
        return children
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(vfComparator)
    }

    /**
     * Returns the children of [parent] filtered by the given glob [pattern].
     * Directories always pass the filter to allow tree navigation.
     * Files must match the glob pattern to be included.
     * Hidden file filtering and sorting are still applied.
     */
    fun getFilteredChildren(parent: VirtualFile, pattern: String): List<VirtualFile> {
        return getChildren(parent).filter { it.isDirectory || getOrCreateGlobFilter(pattern).matches(it.name) }
    }

    /**
     * Returns true if the given [file] is a leaf node (i.e., not a directory).
     */
    fun isLeaf(file: VirtualFile): Boolean = !file.isDirectory

    private fun getOrCreateGlobFilter(pattern: String): GlobFilter {
        if (pattern != cachedGlobPattern || cachedGlobFilter == null) {
            cachedGlobFilter = GlobFilter(pattern)
            cachedGlobPattern = pattern
        }
        return cachedGlobFilter!!
    }
}
