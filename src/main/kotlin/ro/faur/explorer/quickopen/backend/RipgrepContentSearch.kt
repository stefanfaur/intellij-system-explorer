package ro.faur.explorer.quickopen.backend

import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ro.faur.explorer.settings.QuickOpenSettings
import ro.faur.explorer.util.ExplorerErrorNotifier

data class ContentMatch(
    val filePath: String,
    val lineNumber: Int,
    val snippet: String,
    val matchRanges: List<IntRange> = emptyList()
) {
    companion object {
        fun parseJson(line: String): ContentMatch? {
            return try {
                val obj = JsonParser.parseString(line).asJsonObject
                if (obj.get("type")?.asString != "match") return null
                val data = obj.getAsJsonObject("data")
                val path = data.getAsJsonObject("path").get("text").asString
                val lineNum = data.get("line_number").asInt
                val rawLine = data.getAsJsonObject("lines").get("text").asString
                val snippet = rawLine.trimEnd('\n', '\r').take(120)
                val submatches = data.getAsJsonArray("submatches")
                val ranges = submatches.mapNotNull { sm ->
                    val smObj = sm.asJsonObject
                    val byteStart = smObj.get("start").asInt
                    val byteEnd = smObj.get("end").asInt
                    byteRangeToCharRange(snippet, byteStart, byteEnd)
                }
                ContentMatch(path, lineNum, snippet, ranges)
            } catch (_: Exception) { null }
        }

        /** Converts a byte-offset range into a char-offset range within [text]. */
        private fun byteRangeToCharRange(text: String, byteStart: Int, byteEnd: Int): IntRange? {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (byteStart >= bytes.size) return null
            val charStart = String(bytes.copyOfRange(0, byteStart), Charsets.UTF_8).length
            val clampedEnd = byteEnd.coerceAtMost(bytes.size)
            val charEnd = String(bytes.copyOfRange(0, clampedEnd), Charsets.UTF_8).length
            if (charStart >= charEnd) return null
            return charStart until charEnd
        }
    }
}

/**
 * Streams content search results from `rg --json`.
 * Used for the /: query prefix.
 * Debounce (150ms) is enforced by the caller (QuickOpenPanel).
 */
class RipgrepContentSearch(
    private val rgPath: String = try {
        QuickOpenSettings.getInstance().state.ripgrepPath.ifBlank { "rg" }
    } catch (_: Exception) { "rg" },
    private val scope: String
) {
    companion object {
        private val LOG = Logger.getInstance(RipgrepContentSearch::class.java)
        const val DEFAULT_MAX_CONTENT_RESULTS = 200

        private val ALLOWED_FLAGS = setOf("--hidden", "--no-ignore", "--follow")
        private val ALLOWED_PREFIXES = setOf("--type", "--glob")
    }

    private fun validateExtraFlags(flags: String): List<String> {
        if (flags.isBlank()) return emptyList()
        return flags.trim().split("\\s+".toRegex()).filter { token ->
            when {
                token.isBlank() -> false
                token in ALLOWED_FLAGS -> true
                ALLOWED_PREFIXES.any { token.startsWith(it) } -> true
                else -> false
            }
        }
    }

    fun search(pattern: String): Flow<ContentMatch> = flow {
        if (pattern.isBlank()) return@flow
        val s = try { QuickOpenSettings.getInstance().state } catch (_: Exception) { null }
        val maxResults = s?.maxContentResults ?: DEFAULT_MAX_CONTENT_RESULTS

        val cmd = mutableListOf(rgPath, "--json", "--max-count", "1")
        if (s?.ripgrepSearchHidden == true) cmd.add("--hidden")
        if (s?.ripgrepFollowSymlinks == true) cmd.add("--follow")
        if (s?.ripgrepRespectIgnore == false) cmd.add("--no-ignore")
        val depth = s?.ripgrepMaxDepth ?: 0
        if (depth > 0) { cmd.add("--max-depth"); cmd.add(depth.toString()) }
        val extra = s?.ripgrepExtraFlags?.trim() ?: ""
        cmd.addAll(validateExtraFlags(extra))
        cmd.add(pattern)
        cmd.add(scope)

        val process = try {
            ProcessBuilder(cmd).redirectErrorStream(false).start()
        } catch (_: Exception) { return@flow }

        var count = 0
        try {
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (count >= maxResults) break
                    ContentMatch.parseJson(line!!)?.let {
                        emit(it)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Content search error for pattern=$pattern scope=$scope", e)
            ExplorerErrorNotifier.notify(null, "Explorer: Content Search Failed", "Ripgrep search failed for pattern '$pattern'.", e)
        } finally {
            process.destroy()
        }
    }
}
