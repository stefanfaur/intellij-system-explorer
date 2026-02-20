package ro.faur.explorer.quickopen.backend

import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate

/**
 * Uses the nucleo native JNI scorer.
 * If [NucleoNative] is not available, [isAvailable] returns false
 * and the caller should fall back to [FallbackRanker].
 */
class NucleoRanker : RankerBackend {
    override val name = "NucleoRanker (JNI)"

    override fun isAvailable(): Boolean = NucleoNative.isAvailable

    override fun rank(
        query: String,
        candidates: List<SearchCandidate>,
        limit: Int
    ): List<ScoredCandidate> {
        if (!isAvailable()) return emptyList()
        if (query.isBlank()) return candidates.take(limit).map { ScoredCandidate(it, 0.0) }

        return candidates
            .mapNotNull { candidate ->
                val score = NucleoNative.score(candidate.displayName, query)
                if (score < 0) null
                else {
                    val indices = NucleoNative.matchIndices(candidate.displayName, query)
                    val ranges = indicesToRanges(indices)
                    ScoredCandidate(candidate, score.toDouble(), ranges)
                }
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun indicesToRanges(indices: IntArray): List<IntRange> {
        if (indices.isEmpty()) return emptyList()
        val sorted = indices.sorted()
        val ranges = mutableListOf<IntRange>()
        var start = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] != prev + 1) {
                ranges.add(start..prev)
                start = sorted[i]
            }
            prev = sorted[i]
        }
        ranges.add(start..prev)
        return ranges
    }
}
