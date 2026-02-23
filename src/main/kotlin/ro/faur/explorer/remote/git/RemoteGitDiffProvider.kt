package ro.faur.explorer.remote.git

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import ro.faur.explorer.remote.SftpConnectionManager

/**
 * Implements [DiffProvider] for the Remote Git VCS integration.
 *
 * The diff provider is the bridge between IntelliJ's diff viewer and the VCS
 * backend. It answers three questions:
 *
 * 1. What is the current revision of a file? ([getCurrentRevision])
 * 2. What is the "last" revision (tip) of a file? ([getLastRevision])
 * 3. How do I get the content at a given revision? ([createFileContent])
 *
 * Because remote files are not represented as local [VirtualFile]s that map
 * 1-to-1 to the repository layout, the implementation derives the remote path
 * from [VirtualFile.getPath] and falls back to the repository root when the
 * mapping is unclear.
 *
 * @param executor          Executes git commands on the remote host.
 * @param connectionName    Key identifying the remote connection.
 * @param repoPath          Absolute path to the git repository root on the remote.
 * @param connectionManager Provides access to the SFTP client for working-copy reads.
 */
class RemoteGitDiffProvider(
    private val executor: RemoteGitCommandExecutor,
    private val connectionName: String,
    private val repoPath: String,
    private val connectionManager: SftpConnectionManager,
) : DiffProvider {

    /**
     * Returns the current HEAD commit hash for [file], or `null` if it cannot be
     * determined (untracked file, empty repository, connection lost, …).
     */
    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? {
        val remotePath = file.path
        val relPath = relativize(remotePath) ?: return null

        return try {
            // `git log -1 --format=%H -- <path>` returns the commit that last
            // touched this file.  If there are no commits yet it prints nothing.
            val result = executor.executeBlocking(
                repoPath,
                args = arrayOf("log", "-1", "--format=%H", "--", relPath)
            )
            val hash = result.stdout.trim()
            if (result.isSuccess && hash.length == 40) RemoteGitRevisionNumber(hash) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the latest (tip) revision for [file].
     *
     * For our use case this is semantically the same as [getCurrentRevision] —
     * both represent the most recent commit that touched the file.
     */
    override fun getLastRevision(file: VirtualFile): ItemLatestState? {
        val rev = getCurrentRevision(file) ?: return null
        return ItemLatestState(rev, true, false)
    }

    /**
     * Returns the latest revision for a file identified by [filePath].
     *
     * Delegates to the VirtualFile overload by using the path string.
     */
    override fun getLastRevision(filePath: FilePath): ItemLatestState? {
        val remotePath = filePath.path
        val relPath = relativize(remotePath) ?: return null

        return try {
            val result = executor.executeBlocking(
                repoPath,
                args = arrayOf("log", "-1", "--format=%H", "--", relPath)
            )
            val hash = result.stdout.trim()
            if (result.isSuccess && hash.length == 40) {
                ItemLatestState(RemoteGitRevisionNumber(hash), true, false)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the latest committed revision for [file].
     *
     * This is equivalent to [getCurrentRevision] for remote git — both return
     * the most recent commit that touched the file.
     */
    override fun getLatestCommittedRevision(file: VirtualFile): VcsRevisionNumber? =
        getCurrentRevision(file)

    /**
     * Creates a [ContentRevision] that can fetch the file content at [revisionNumber].
     *
     * The [filePath] argument identifies the file; [revisionNumber] must be a
     * [RemoteGitRevisionNumber] (or any [VcsRevisionNumber] whose [asString]
     * returns a valid git revision expression).
     */
    override fun createFileContent(revisionNumber: VcsRevisionNumber, file: VirtualFile): ContentRevision? {
        val remotePath = file.path
        val relPath = relativize(remotePath) ?: return null
        return RemoteGitContentRevision(executor, repoPath, relPath, revisionNumber.asString())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the path of [remotePath] relative to [repoPath], or `null` if the
     * path is outside the repository.
     */
    private fun relativize(remotePath: String): String? {
        if (remotePath == repoPath) return "."
        val prefix = "$repoPath/"
        return if (remotePath.startsWith(prefix)) remotePath.removePrefix(prefix) else null
    }
}
