package ro.faur.explorer.quickopen.backend

import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate

interface RankerBackend {
    fun isAvailable(): Boolean
    fun rank(
        query: String,
        candidates: List<SearchCandidate>,
        limit: Int
    ): List<ScoredCandidate>
    val name: String
}
