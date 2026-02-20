package ro.faur.explorer.quickopen.backend

import com.intellij.psi.codeStyle.NameUtil
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate

/**
 * Thin wrapper around IntelliJ's built-in MinusculeMatcher.
 * Zero external dependencies. Handles CamelCase, acronyms, and prefix matching.
 */
class MinusculeMatcherRanker : RankerBackend {
    override val name = "MinusculeMatcher"
    override fun isAvailable() = true

    override fun rank(
        query: String,
        candidates: List<SearchCandidate>,
        limit: Int
    ): List<ScoredCandidate> {
        if (query.isBlank()) return candidates.take(limit).map { ScoredCandidate(it, 0.0) }

        val pattern = "*${query.trim()}"
        val matcher = try {
            NameUtil.buildMatcher(pattern)
                .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
                .preferringStartMatches()
                .build()
        } catch (_: Exception) {
            return emptyList()
        }

        return candidates
            .mapNotNull { candidate ->
                val degree = matcher.matchingDegree(candidate.displayName)
                if (degree == Int.MIN_VALUE) null
                else {
                    // matchingFragments returns FList<TextRange> — already iterable, no .fragments() needed
                    val fragments = matcher.matchingFragments(candidate.displayName)
                    val ranges = fragments
                        ?.map { textRange -> textRange.startOffset until textRange.endOffset }
                        ?: emptyList()
                    ScoredCandidate(
                        candidate = candidate,
                        score = degree.toDouble(),
                        matchedRanges = ranges
                    )
                }
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
