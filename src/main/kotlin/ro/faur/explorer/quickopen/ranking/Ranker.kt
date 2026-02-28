package ro.faur.explorer.quickopen.ranking

import ro.faur.explorer.quickopen.backend.RankerBackend
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import ro.faur.explorer.settings.QuickOpenSettings

/**
 * Orchestrates the full scoring pipeline:
 *   finalScore = text×0.50 + frecency(recency+freq)×0.32 + prox×0.08 + boosts(0.10)
 * The recency/frequency split within the frecency budget is configurable via QuickOpenSettings.
 *
 * textScorer can be MinusculeMatcherRanker, FallbackRanker, or NucleoRanker.
 * All produce ScoredCandidate with the same textScore semantics.
 */
class Ranker(private val textScorer: RankerBackend) {

    fun rank(
        query: String,
        candidates: List<SearchCandidate>,
        currentPath: String,
        limit: Int = 100
    ): List<ScoredCandidate> {
        val store = FrecencyStore.getInstance()
        val qs = try { QuickOpenSettings.getInstance().state } catch (_: Exception) { null }
        val recencyWeight = (qs?.frecencyRecencyWeight ?: 60) / 100.0 * 0.32  // 0.32 = total frecency budget
        val freqWeight = (1.0 - (qs?.frecencyRecencyWeight ?: 60) / 100.0) * 0.32
        val showDebug = qs?.showScorerDebug ?: false

        val textScored = textScorer.rank(query, candidates, candidates.size)
        if (textScored.isEmpty()) return emptyList()

        val maxText = textScored.maxOf { it.score }.coerceAtLeast(1.0)

        return textScored
            .map { sc ->
                val path = sc.candidate.fullPath
                val normText = if (maxText > 0) sc.score / maxText else 0.0
                val recency = store.recencyScore(path)
                val freq = store.frequencyScore(path)
                val proximity = proximityScore(path, currentPath)
                val bookmarkBoost = if (sc.candidate.signals.isBookmarked) 0.05 else 0.0
                val editorBoost = if (sc.candidate.signals.isOpenInEditor) 0.05 else 0.0

                val finalScore = normText * 0.50 +
                        recency * recencyWeight +
                        freq * freqWeight +
                        proximity * 0.08 +
                        bookmarkBoost +
                        editorBoost

                val displayName = if (showDebug) "${sc.candidate.displayName} [%.3f]".format(finalScore)
                                  else sc.candidate.displayName
                sc.copy(score = finalScore, candidate = sc.candidate.copy(displayName = displayName))
            }
            .sortedWith(
                compareBy<ScoredCandidate> { typeGroup(it.candidate) }
                    .thenByDescending { it.score }
                    .thenBy { it.candidate.displayName }
                    .thenBy { it.candidate.fullPath.length }
                    .thenBy { it.candidate.fullPath }
            )
            .take(limit)
    }

    private fun typeGroup(candidate: SearchCandidate): Int = when (candidate.type) {
        CandidateType.BOOKMARK, CandidateType.RECENT -> 0
        CandidateType.FILE, CandidateType.OPEN_EDITOR, CandidateType.CONTENT_MATCH -> 1
        CandidateType.DIRECTORY -> 2
        else -> 3
    }

    private fun proximityScore(path: String, currentPath: String): Double {
        if (currentPath.isBlank()) return 0.0
        if (path == currentPath) return 1.0
        if (!path.startsWith(currentPath)) return 0.0
        val rel = path.removePrefix(currentPath)
        val extraSegments = rel.count { it == '/' }
        return (1.0 - extraSegments * 0.15).coerceAtLeast(0.0)
    }
}
