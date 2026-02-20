package ro.faur.explorer.quickopen.ui

import ro.faur.explorer.quickopen.model.ScoredCandidate

data class SearchResult(
    val scored: ScoredCandidate? = null,  // null = skeleton row or group header
    val isGroupHeader: Boolean = false,
    val groupName: String = "",
    val gitStatus: String = ""  // "M", "?", "✓", or "" if not in VCS
)
