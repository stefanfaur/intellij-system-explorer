package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ContentMatch(
    val filePath: String,
    val lineNumber: Int,
    val snippet: String
) {
    companion object {
        // rg --no-heading format: path:line:content
        fun parse(line: String): ContentMatch? {
            val parts = line.split(":", limit = 3)
            if (parts.size < 3) return null
            return try {
                ContentMatch(parts[0], parts[1].toInt(), parts[2].take(120))
            } catch (_: Exception) { null }
        }
    }
}

/**
 * Streams content search results from `rg --line-number`.
 * Used for the /: query prefix.
 * Debounce (150ms) is enforced by the caller (QuickOpenPanel).
 */
class RipgrepContentSearch(
    private val rgPath: String = "rg",
    private val scope: String
) {
    companion object {
        private val LOG = Logger.getInstance(RipgrepContentSearch::class.java)
        const val MAX_CONTENT_RESULTS = 200
    }

    fun search(pattern: String): Flow<ContentMatch> = flow {
        if (pattern.isBlank()) return@flow
        val process = try {
            ProcessBuilder(
                rgPath,
                "--line-number",
                "--no-heading",
                "--color=never",
                "--max-count", "1",
                pattern,
                scope
            ).redirectErrorStream(false).start()
        } catch (_: Exception) {
            return@flow
        }

        var count = 0
        try {
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (count >= MAX_CONTENT_RESULTS) break
                    ContentMatch.parse(line!!)?.let {
                        emit(it)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Content search error for pattern=$pattern scope=$scope", e)
        } finally {
            process.destroy()
        }
    }
}
