package ro.faur.explorer.remote.git

enum class GitFileStatus {
    MODIFIED, ADDED, DELETED, RENAMED, COPIED, UNTRACKED, IGNORED, UNMERGED
}

data class GitStatusEntry(
    val status: GitFileStatus,
    val relativePath: String,
    val originalPath: String? = null,
    val isDirectory: Boolean = false,
)

object GitStatusParser {
    fun parseLine(line: String): GitStatusEntry? {
        if (line.isBlank()) return null
        return when {
            line.startsWith("1 ") -> parseChangedEntry(line)
            line.startsWith("2 ") -> parseRenamedEntry(line)
            line.startsWith("? ") -> GitStatusEntry(GitFileStatus.UNTRACKED, line.substring(2))
            line.startsWith("! ") -> GitStatusEntry(GitFileStatus.IGNORED, line.substring(2))
            line.startsWith("u ") -> GitStatusEntry(GitFileStatus.UNMERGED, parseUnmergedPath(line))
            else -> null
        }
    }

    private fun parseChangedEntry(line: String): GitStatusEntry? {
        // Format: 1 XY sub mH mI mW hH hI path
        val parts = line.split(" ", limit = 9)
        if (parts.size < 9) return null
        val xy = parts[1]
        if (xy.length < 2) return null
        val path = parts[8]
        val status = when {
            xy[0] == 'D' || xy[1] == 'D' -> GitFileStatus.DELETED
            xy[0] == 'A' || xy[1] == 'A' -> GitFileStatus.ADDED
            xy[0] == 'M' || xy[1] == 'M' -> GitFileStatus.MODIFIED
            else -> GitFileStatus.MODIFIED
        }
        return GitStatusEntry(status, path)
    }

    private fun parseRenamedEntry(line: String): GitStatusEntry? {
        // Format: 2 XY sub mH mI mW hH hI score path\torigPath
        val parts = line.split(" ", limit = 10)
        if (parts.size < 10) return null
        val pathPart = parts[9]
        val paths = pathPart.split("\t", limit = 2)
        return GitStatusEntry(
            GitFileStatus.RENAMED,
            paths[0],
            originalPath = paths.getOrNull(1)
        )
    }

    private fun parseUnmergedPath(line: String): String {
        val parts = line.split(" ", limit = 11)
        return if (parts.size >= 11) parts[10] else ""
    }
}
