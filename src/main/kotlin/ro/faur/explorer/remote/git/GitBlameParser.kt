package ro.faur.explorer.remote.git

data class BlameLine(
    val commitHash: String,
    val author: String,
    val authorEmail: String,
    val timestamp: Long,
    val lineNumber: Int,
    val content: String,
    val summary: String = "",
)

object GitBlameParser {
    fun parse(porcelainOutput: String): List<BlameLine> {
        val lines = porcelainOutput.lines()
        val result = mutableListOf<BlameLine>()
        var i = 0
        while (i < lines.size) {
            val headerLine = lines[i]
            if (headerLine.isBlank()) { i++; continue }
            val headerParts = headerLine.split(" ", limit = 4)
            if (headerParts.size < 3) { i++; continue }
            val hash = headerParts[0]
            if (hash.length != 40) { i++; continue }
            val lineNum = headerParts[2].toIntOrNull() ?: 0
            i++
            var author = ""
            var authorEmail = ""
            var authorTime = 0L
            var summary = ""
            var content = ""
            while (i < lines.size) {
                val line = lines[i]
                when {
                    line.startsWith("author ") -> author = line.removePrefix("author ")
                    line.startsWith("author-mail ") -> authorEmail = line.removePrefix("author-mail ").trim('<', '>')
                    line.startsWith("author-time ") -> authorTime = line.removePrefix("author-time ").toLongOrNull() ?: 0
                    line.startsWith("summary ") -> summary = line.removePrefix("summary ")
                    line.startsWith("\t") -> { content = line.removePrefix("\t"); i++; break }
                }
                i++
            }
            result.add(BlameLine(hash, author, authorEmail, authorTime, lineNum, content, summary))
        }
        return result
    }
}
