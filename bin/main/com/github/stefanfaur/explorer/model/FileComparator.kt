package com.github.stefanfaur.explorer.model

class FileComparator(private val foldersFirst: Boolean) : Comparator<FileEntry> {

    override fun compare(a: FileEntry, b: FileEntry): Int {
        if (foldersFirst) {
            // Directories before files
            if (a.isDirectory && !b.isDirectory) return -1
            if (!a.isDirectory && b.isDirectory) return 1
        }
        // Within the same group (or when foldersFirst is false), sort alphabetically case-insensitive
        return a.name.compareTo(b.name, ignoreCase = true)
    }
}
