package com.github.stefanfaur.explorer.util

object FileSizeFormatter {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < UNITS.size - 1) {
            size /= 1024.0
            unitIndex++
        }

        return if (unitIndex == 0) {
            "${bytes} B"
        } else {
            "${"%.1f".format(size)} ${UNITS[unitIndex]}"
        }
    }

    /**
     * Counts direct children of a directory (non-recursive).
     * Returns 0 if the file is not a directory or children can't be read.
     */
    fun countDirectChildren(dir: com.intellij.openapi.vfs.VirtualFile): Int {
        if (!dir.isDirectory) return 0
        return dir.children?.size ?: 0
    }

    /**
     * Computes the total size of all files in a directory (non-recursive, immediate children only).
     * Directories themselves contribute 0 bytes; only their child file sizes are counted.
     */
    fun computeDirectoryImmediateSize(dir: com.intellij.openapi.vfs.VirtualFile): Long {
        if (!dir.isDirectory) return dir.length
        return dir.children?.filter { !it.isDirectory }?.sumOf { it.length } ?: 0L
    }
}
