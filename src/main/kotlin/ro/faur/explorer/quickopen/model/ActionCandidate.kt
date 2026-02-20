package ro.faur.explorer.quickopen.model

// Represents a callable explorer action as a search result
data class ActionCandidate(
    val actionId: String,
    val displayName: String,
    val description: String,
    val handler: () -> Unit
)
