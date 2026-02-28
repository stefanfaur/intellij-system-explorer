package ro.faur.explorer.quickopen.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ro.faur.explorer.quickopen.index.LuceneIndexManager

class LuceneEnumerator(private val manager: LuceneIndexManager) : EnumeratorBackend {
    override val name = "LuceneEnumerator"
    override fun isAvailable(): Boolean = true  // manager handles corrupt-open internally

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        // Empty query triggers MatchAllDocsQuery inside searchPaths (implemented in plan 08-01).
        // This returns all indexed paths up to maxResults — correct behavior for path enumeration.
        manager.searchPaths("", maxResults).forEach { path -> emit(path) }
    }
}
