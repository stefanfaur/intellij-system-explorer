package ro.faur.explorer.quickopen.model

data class SearchCandidate(
    val id: String,                          // stable: sha256(fullPath + type)
    val displayName: String,                 // last path segment or action name
    val fullPath: String,                    // absolute path or action id
    val parentPath: String,                  // parent dir, empty for actions
    val type: CandidateType,
    val signals: UsageSignals = UsageSignals(),
    val contentSnippet: String? = null,      // for CONTENT_MATCH results
    val extra: Map<String, Any> = emptyMap() // extension, childCount, gitStatus…
)
