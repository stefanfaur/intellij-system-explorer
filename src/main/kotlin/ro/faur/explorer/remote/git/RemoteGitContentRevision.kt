package ro.faur.explorer.remote.git

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil

/**
 * A [ContentRevision] that retrieves file content at a specific git revision via `git show`.
 *
 * The revision format accepted is any expression that `git show` understands, e.g. a
 * full or abbreviated commit hash, `HEAD`, `HEAD~1`, etc.
 *
 * @param executor   Executor that runs git commands on the remote host.
 * @param repoPath   Absolute path to the git repository root on the remote host.
 * @param relativePath Path of the file relative to [repoPath].
 * @param revision   Git revision expression (commit hash, branch name, …).
 */
class RemoteGitContentRevision(
    private val executor: RemoteGitCommandExecutor,
    private val repoPath: String,
    private val relativePath: String,
    private val revision: String,
) : ContentRevision {

    private val filePath: FilePath by lazy {
        // The path does not exist locally; VcsUtil creates a FilePath for a
        // virtual path that is used only for display and diff purposes.
        VcsUtil.getFilePath("$repoPath/$relativePath", false)
    }

    /**
     * Returns the file content as it existed at [revision], or `null` when:
     * - the file did not exist at that revision, or
     * - the remote command fails for any reason.
     *
     * @throws VcsException if the command execution itself errors out (not merely a non-zero exit).
     */
    override fun getContent(): String? {
        return try {
            val result = executor.executeBlocking(
                repoPath,
                args = arrayOf("show", "$revision:$relativePath")
            )
            if (result.isSuccess) result.stdout else null
        } catch (e: IllegalStateException) {
            throw VcsException("Failed to fetch content of $relativePath at $revision: ${e.message}", e)
        }
    }

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber =
        RemoteGitRevisionNumber(revision)
}

/**
 * A lightweight [VcsRevisionNumber] that wraps an arbitrary git revision string.
 */
class RemoteGitRevisionNumber(private val rev: String) : VcsRevisionNumber {
    override fun asString(): String = rev
    override fun compareTo(other: VcsRevisionNumber): Int {
        if (other is RemoteGitRevisionNumber) return rev.compareTo(other.rev)
        return asString().compareTo(other.asString())
    }
    override fun toString(): String = rev
}
