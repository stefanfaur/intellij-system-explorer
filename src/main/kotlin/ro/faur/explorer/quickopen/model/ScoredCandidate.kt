package ro.faur.explorer.quickopen.model

data class ScoredCandidate(
    val candidate: SearchCandidate,
    val score: Double,
    val matchedRanges: List<IntRange> = emptyList()
)
