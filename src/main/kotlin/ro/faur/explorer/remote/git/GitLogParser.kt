package ro.faur.explorer.remote.git

/**
 * Parsed representation of a single `git log` entry.
 */
data class GitLogEntry(
    val hash: String,
    val authorName: String,
    val authorEmail: String,
    val timestamp: Long,
    val subject: String,
)

/**
 * Parses the output of `git log --format=%H|%an|%ae|%at|%s`.
 *
 * Each line is pipe-delimited with limit=5 to allow pipe characters in the subject.
 * Malformed lines (too few fields, non-40-char hash, unparseable epoch) are skipped.
 */
object GitLogParser {

    fun parse(lines: List<String>): List<GitLogEntry> {
        return lines.mapNotNull { line -> parseLine(line) }
    }

    fun parseLine(line: String): GitLogEntry? {
        if (line.isBlank()) return null
        val parts = line.split("|", limit = 5)
        if (parts.size < 5) return null
        val hash = parts[0].trim()
        if (hash.length != 40) return null
        val author = parts[1]
        val email = parts[2]
        val epochSeconds = parts[3].toLongOrNull() ?: return null
        val subject = parts[4]
        return GitLogEntry(
            hash = hash,
            authorName = author,
            authorEmail = email,
            timestamp = epochSeconds,
            subject = subject,
        )
    }
}
