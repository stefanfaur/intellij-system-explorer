package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ro.faur.explorer.settings.QuickOpenSettings

/**
 * Enumerates files using `rg --files` (ripgrep).
 * Used ONLY for paths outside IntelliJ's VFS (external directories).
 * Respects the 50k hard cap and 10s timeout (enforced by CandidatePool caller).
 *
 * Falls back to [VfsEnumerator] if rg is not installed.
 */
class RipgrepEnumerator(private val rgPath: String = "rg") : EnumeratorBackend {
    override val name = "RipgrepEnumerator"

    companion object {
        private val LOG = Logger.getInstance(RipgrepEnumerator::class.java)

        /** Detects if ripgrep is on PATH. Returns the path or null. */
        fun detect(): String? {
            val candidates = listOf("rg", "/opt/homebrew/bin/rg", "/usr/local/bin/rg", "/usr/bin/rg")
            for (cmd in candidates) {
                return try {
                    val p = ProcessBuilder(cmd, "--version").start()
                    if (p.waitFor() == 0) cmd else continue
                } catch (_: Exception) { continue }
            }
            return null
        }
    }

    private val available: Boolean by lazy {
        try { ProcessBuilder(rgPath, "--version").start().waitFor() == 0 }
        catch (_: Exception) { false }
    }
    override fun isAvailable(): Boolean = available

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        val s = try { QuickOpenSettings.getInstance().state } catch (_: Exception) { null }
        val cmd = mutableListOf(rgPath, "--files", "--color=never")
        if (s?.ripgrepSearchHidden == true) cmd.add("--hidden")
        if (s?.ripgrepFollowSymlinks == true) cmd.add("--follow")
        if (s?.ripgrepRespectIgnore == false) cmd.add("--no-ignore")
        val depth = s?.ripgrepMaxDepth ?: 0
        if (depth > 0) { cmd.add("--max-depth"); cmd.add(depth.toString()) }
        val extra = s?.ripgrepExtraFlags?.trim() ?: ""
        if (extra.isNotBlank()) cmd.addAll(extra.split("\\s+".toRegex()))
        cmd.add(root)

        val process = try {
            ProcessBuilder(cmd).redirectErrorStream(false).start()
        } catch (_: Exception) {
            return@flow
        }

        var count = 0
        try {
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (count >= maxResults) break
                    emit(line!!)
                    count++
                }
            }
        } finally {
            process.destroy()
        }
    }
}
