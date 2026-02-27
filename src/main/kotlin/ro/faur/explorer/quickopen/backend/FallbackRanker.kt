package ro.faur.explorer.quickopen.backend

import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import kotlin.math.max

/**
 * Pure-Kotlin Smith-Waterman fuzzy scorer.
 * Used as the fallback when nucleo JNI is unavailable, and also as the
 * reference implementation for parity tests against NucleoRanker.
 *
 * Scoring weights (match details):
 *   - Consecutive match bonus: +0.3 per run
 *   - Prefix bonus: +0.2 if query matches start of displayName
 *   - Exact name bonus: +0.5 if query matches displayName exactly (case-insensitive)
 *   - Gap penalty: -0.1 per skipped character
 */
class FallbackRanker : RankerBackend {
    override val name = "FallbackRanker (Kotlin Smith-Waterman)"
    override fun isAvailable() = true

    override fun rank(
        query: String,
        candidates: List<SearchCandidate>,
        limit: Int
    ): List<ScoredCandidate> {
        if (query.isBlank()) return candidates.take(limit).map { ScoredCandidate(it, 0.0) }

        val q = query.lowercase()
        return candidates
            .mapNotNull { candidate ->
                val (score, ranges) = smithWaterman(q, candidate.displayName.lowercase())
                if (score <= 0.0) null
                else ScoredCandidate(candidate, applyBonuses(score, q, candidate.displayName.lowercase()), ranges)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun applyBonuses(base: Double, query: String, name: String): Double {
        var score = base
        if (name == query) score += 0.5
        else if (name.startsWith(query)) score += 0.2
        return score
    }

    // Returns (score, matchedRanges). Score < 0 means no match found.
    private fun smithWaterman(query: String, text: String): Pair<Double, List<IntRange>> {
        if (query.isEmpty()) return 0.0 to emptyList()

        val m = query.length
        val n = text.length
        if (m > n) return -1.0 to emptyList()

        // H[i][j] = best score ending at query[i-1], text[j-1]
        val H = Array(m + 1) { DoubleArray(n + 1) }
        var best = -1.0
        var bestJ = -1

        for (i in 1..m) {
            for (j in 1..n) {
                val matchScore = if (query[i - 1] == text[j - 1]) 1.0 else -0.5
                val consecutive = if (query[i - 1] == text[j - 1] && i > 1 && j > 1 && H[i - 1][j - 1] > 0) 0.3 else 0.0
                H[i][j] = max(0.0, H[i - 1][j - 1] + matchScore + consecutive)
                if (i == m && H[i][j] > best) {
                    best = H[i][j]
                    bestJ = j
                }
            }
        }

        if (best < 0.0 || bestJ < 0) return -1.0 to emptyList()

        // Traceback to find matched ranges (strict diagonal — no leftward scan)
        val matched = mutableListOf<Int>()
        var ci = m
        var cj = bestJ
        while (ci > 0 && cj > 0 && H[ci][cj] > 0) {
            matched.add(cj - 1)
            ci--; cj--
        }
        matched.reverse()

        // Collapse consecutive indices into IntRanges
        val ranges = mutableListOf<IntRange>()
        var start = -1
        var prev = -2
        for (idx in matched) {
            if (idx != prev + 1) {
                if (start >= 0) ranges.add(start..prev)
                start = idx
            }
            prev = idx
        }
        if (start >= 0) ranges.add(start..prev)

        return best to ranges
    }
}
