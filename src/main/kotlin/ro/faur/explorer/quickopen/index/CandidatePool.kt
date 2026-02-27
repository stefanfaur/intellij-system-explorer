package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import ro.faur.explorer.quickopen.backend.EnumeratorBackend
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import ro.faur.explorer.settings.QuickOpenSettings
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages the in-memory candidate pool for the current root.
 * Respects scope guardrails:
 *   - Hard cap at MAX_CANDIDATES (default 50,000)
 *   - Configurable timeout kill on enumerator (from QuickOpenSettings)
 *   - Network mount rejection (future)
 *
 * Thread-safe: all mutations happen on a background coroutine;
 * [getCandidates] is safe to call from any thread.
 */
class CandidatePool(
    private val enumerator: EnumeratorBackend,
    private val maxCandidates: Int = try {
        QuickOpenSettings.getInstance().state.maxIndexSize
    } catch (_: Exception) { MAX_CANDIDATES }
) {

    companion object {
        const val MAX_CANDIDATES = 50_000
        private val LOG = Logger.getInstance(CandidatePool::class.java)
    }

    private val _candidates = CopyOnWriteArrayList<SearchCandidate>()
    private var enumerationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isTruncated: Boolean = false
        private set

    fun getCandidates(): List<SearchCandidate> = _candidates.toList()

    /**
     * Starts enumerating [root] asynchronously.
     * Cancels any in-progress enumeration first.
     * Calls [onUpdate] on EDT when the pool finishes populating.
     */
    fun refreshAsync(root: String, onUpdate: () -> Unit = {}) {
        val previousJob = enumerationJob
        enumerationJob = scope.launch {
            previousJob?.cancelAndJoin()   // wait for old job to stop before clearing
            _candidates.clear()
            isTruncated = false
            val timeoutMs = try {
                QuickOpenSettings.getInstance().state.indexEnumerationTimeoutSec * 1000L
            } catch (_: Exception) { 10_000L }
            try {
                withTimeout(timeoutMs) {
                    try {
                        enumerator.enumerate(root, maxCandidates + 1).collect { path ->
                            if (_candidates.size >= maxCandidates) {
                                isTruncated = true
                                return@collect // cap reached; rely on enumerator's maxResults to stop the flow
                            }
                            val file = File(path)
                            val store = FrecencyStore.getInstance()
                            _candidates.add(SearchCandidate(
                                id = "fs:$path",
                                displayName = file.name,
                                fullPath = path,
                                parentPath = file.parent ?: "",
                                type = if (file.isDirectory) CandidateType.DIRECTORY else CandidateType.FILE,
                                signals = store.getSignals(path)
                            ))
                        }
                    } catch (e: CancellationException) {
                        LOG.debug("Enumeration cancelled for $root")
                        throw e // re-throw so external cancel() propagates correctly
                    } catch (e: Exception) {
                        LOG.warn("Enumeration error for $root", e)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                LOG.warn("Enumeration timed out for $root after ${timeoutMs / 1000}s")
            }
            // Call onUpdate directly from the background thread.
            // Callers that need EDT dispatch (e.g. QuickOpenPanel) wrap it themselves.
            onUpdate()
        }
    }

    fun cancel() {
        enumerationJob?.cancel()
    }
}
