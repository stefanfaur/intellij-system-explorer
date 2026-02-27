package ro.faur.explorer.quickopen.ranking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import ro.faur.explorer.quickopen.model.UsageSignals
import ro.faur.explorer.settings.QuickOpenSettings
import kotlin.math.exp
import kotlin.math.ln

/**
 * Application-scoped persistent store for frecency usage signals.
 * Persists to explorerFrecency.xml and survives IDE restart.
 * Thread-safe for reads; writes must be on EDT or synchronized.
 */
@State(
    name = "ro.faur.explorer.quickopen.FrecencyStore",
    storages = [Storage("explorerFrecency.xml")]
)
class FrecencyStore : PersistentStateComponent<FrecencyStore.State> {

    class Entry {
        var lastUsedMs: Long = 0L
        var useCount: Int = 0
        var isBookmarked: Boolean = false
        var branch: String = ""

        constructor()
        constructor(lastUsedMs: Long, useCount: Int, isBookmarked: Boolean, branch: String) {
            this.lastUsedMs = lastUsedMs
            this.useCount = useCount
            this.isBookmarked = isBookmarked
            this.branch = branch
        }
    }

    class State {
        var entries: MutableMap<String, Entry> = mutableMapOf()
        var maxUseCount: Int = 1
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    @Synchronized
    fun recordVisit(path: String, branch: String? = null) {
        val entry = myState.entries.getOrPut(path) { Entry() }
        entry.lastUsedMs = System.currentTimeMillis()
        entry.useCount++
        if (branch != null) entry.branch = branch
        if (entry.useCount > myState.maxUseCount) myState.maxUseCount = entry.useCount
        evictIfNeeded()
    }

    @Synchronized
    fun getSignals(path: String): UsageSignals {
        val entry = myState.entries[path] ?: return UsageSignals()
        return UsageSignals(
            lastUsedMs = entry.lastUsedMs,
            useCount = entry.useCount,
            isBookmarked = entry.isBookmarked,
            currentBranch = entry.branch.ifBlank { null }
        )
    }

    @Synchronized
    fun setBookmarked(path: String, bookmarked: Boolean) {
        myState.entries.getOrPut(path) { Entry() }.isBookmarked = bookmarked
    }

    private fun evictIfNeeded() {
        if (myState.entries.size > MAX_ENTRIES) {
            val toRemove = myState.entries.entries
                .sortedBy { it.value.lastUsedMs }
                .take(myState.entries.size - MAX_ENTRIES)
            toRemove.forEach { myState.entries.remove(it.key) }
        }
    }

    /** Computes [0,1] recency score using exponential time-decay with configurable half-life. */
    @Synchronized
    fun recencyScore(path: String): Double {
        val entry = myState.entries[path] ?: return 0.0
        val hoursAgo = (System.currentTimeMillis() - entry.lastUsedMs) / 3_600_000.0
        val halfLife = try {
            QuickOpenSettings.getInstance().state.frecencyHalfLifeHours
        } catch (_: Exception) { 24.0 }
        return exp(-hoursAgo * ln(2.0) / halfLife)
    }

    /** Computes [0,1] frequency score using log-scaled visit count. */
    @Synchronized
    fun frequencyScore(path: String): Double {
        val entry = myState.entries[path] ?: return 0.0
        val max = myState.maxUseCount.coerceAtLeast(1)
        return ln(entry.useCount + 1.0) / ln(max + 1.0)
    }

    companion object {
        private const val MAX_ENTRIES = 10_000

        fun getInstance(): FrecencyStore =
            ApplicationManager.getApplication().getService(FrecencyStore::class.java)
    }
}
