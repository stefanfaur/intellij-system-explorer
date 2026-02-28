package ro.faur.explorer.quickopen.backend

import ro.faur.explorer.quickopen.index.LuceneIndexManager

class LuceneContentSearch(private val manager: LuceneIndexManager) {

    /**
     * Searches indexed content for [pattern].
     * Returns a list of (filePath, snippet) pairs.
     * Returns empty list if pattern is blank or if search throws.
     */
    fun search(pattern: String, maxResults: Int = 200): List<Pair<String, String>> {
        if (pattern.isBlank()) return emptyList()
        return try {
            manager.searchContent(pattern, maxResults)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
