package ro.faur.explorer.quickopen.query

/**
 * Parses the raw query string into a structured [ParsedQuery].
 *
 * Prefix rules (first match wins):
 *   d:   → directories only
 *   f:   → files only
 *   b:   → bookmarks only
 *   r:   → recent paths only
 *   >    → command/action mode
 *   @ext:ext → extension filter (appended to current mode)
 *   @in:path → path scope filter
 *   ~    → regex mode
 *   /:   → content search (ripgrep) — handled separately in Phase 6
 */
data class ParsedQuery(
    val rawText: String,
    val searchText: String,           // the effective query text after prefix stripped
    val mode: QueryMode,
    val extensionFilter: String?,     // set when @ext: prefix present
    val scopeFilter: String?          // set when @in: prefix present
)

enum class QueryMode {
    UNIFIED,      // default: dirs + files + recent + bookmarks + actions
    DIRS_ONLY,    // d:
    FILES_ONLY,   // f:
    BOOKMARKS_ONLY, // b:
    RECENT_ONLY,  // r:
    COMMAND,      // >
    REGEX,        // ~
    CONTENT_SEARCH // /: (Phase 6)
}

object QueryParser {
    fun parse(raw: String): ParsedQuery {
        val trimmed = raw.trim()

        var mode = QueryMode.UNIFIED
        var text = trimmed
        var extFilter: String? = null
        var scopeFilter: String? = null

        when {
            trimmed.startsWith("d:") -> { mode = QueryMode.DIRS_ONLY;  text = trimmed.removePrefix("d:").trim() }
            trimmed.startsWith("f:") -> { mode = QueryMode.FILES_ONLY;  text = trimmed.removePrefix("f:").trim() }
            trimmed.startsWith("b:") -> { mode = QueryMode.BOOKMARKS_ONLY; text = trimmed.removePrefix("b:").trim() }
            trimmed.startsWith("r:") -> { mode = QueryMode.RECENT_ONLY; text = trimmed.removePrefix("r:").trim() }
            trimmed.startsWith(">")  -> { mode = QueryMode.COMMAND;     text = trimmed.removePrefix(">").trim() }
            trimmed.startsWith("~")  -> { mode = QueryMode.REGEX;       text = trimmed.removePrefix("~").trim() }
            trimmed.startsWith("/:") -> { mode = QueryMode.CONTENT_SEARCH; text = trimmed.removePrefix("/:").trim() }
        }

        // Extract @ext: modifier
        val extRegex = Regex("@ext:(\\S+)")
        extRegex.find(text)?.let {
            extFilter = it.groupValues[1]
            text = text.replace(it.value, "").trim()
        }

        // Extract @in: modifier
        val inRegex = Regex("@in:(\\S+)")
        inRegex.find(text)?.let {
            scopeFilter = it.groupValues[1]
            text = text.replace(it.value, "").trim()
        }

        return ParsedQuery(
            rawText = raw,
            searchText = text,
            mode = mode,
            extensionFilter = extFilter,
            scopeFilter = scopeFilter
        )
    }
}
