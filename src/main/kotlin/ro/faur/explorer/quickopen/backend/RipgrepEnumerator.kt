package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    override fun isAvailable(): Boolean = try {
        ProcessBuilder(rgPath, "--version").start().waitFor() == 0
    } catch (_: Exception) { false }

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        val process = try {
            ProcessBuilder(
                rgPath, "--files", "--color=never", "--hidden", root
            ).redirectErrorStream(false).start()
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
