package ro.faur.explorer.remote.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.vcsUtil.VcsUtil
import ro.faur.explorer.remote.SftpConnectionManager

/**
 * Implements [ChangeProvider] for the Remote Git VCS integration.
 *
 * On each refresh cycle the IntelliJ VCS framework calls [getChanges]. This
 * implementation runs `git status --porcelain=v2` on the remote host, parses
 * the output with [GitStatusParser], constructs [Change] objects with
 * appropriate before/after [ContentRevision]s, and feeds them to the supplied
 * [ChangelistBuilder]. It also updates [RemoteGitStatusCache] so that the tree
 * cell renderer can colour file nodes without re-running git.
 *
 * @param executor          Executes git commands on the remote host.
 * @param connectionName    Key identifying the remote connection.
 * @param repoPath          Absolute path to the git repository root on the remote.
 * @param connectionManager Provides access to the SFTP client for working-copy reads.
 */
class RemoteGitChangeProvider(
    private val executor: RemoteGitCommandExecutor,
    private val connectionName: String,
    private val repoPath: String,
    private val connectionManager: SftpConnectionManager,
) : ChangeProvider {

    private val LOG = Logger.getInstance(RemoteGitChangeProvider::class.java)

    @Throws(VcsException::class)
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        progress.text = "Refreshing Remote Git status…"

        val result = try {
            executor.executeBlocking(
                repoPath,
                args = arrayOf("status", "--porcelain=v2", "--untracked-files=all")
            )
        } catch (e: IllegalStateException) {
            throw VcsException("Remote Git status failed for $connectionName:$repoPath — ${e.message}", e)
        }

        if (!result.isSuccess) {
            LOG.debug("git status returned non-zero (exit=${result.exitCode}) for $connectionName:$repoPath — ${result.stderr.trim()}")
            return
        }

        val statusLines = result.stdoutLines

        // Update the cache so the tree renderer can read statuses without extra SSH calls.
        RemoteGitStatusCache.update(connectionName, repoPath, statusLines)

        // Resolve the HEAD commit hash once; used as the "before" revision for all changes.
        val headHash: String? = resolveHead()

        for (line in statusLines) {
            progress.checkCanceled()
            val entry = GitStatusParser.parseLine(line) ?: continue
            val absolutePath = "$repoPath/${entry.relativePath}"

            when (entry.status) {
                GitFileStatus.UNTRACKED -> {
                    val fp = VcsUtil.getFilePath(absolutePath, entry.isDirectory)
                    builder.processUnversionedFile(fp)
                }

                GitFileStatus.IGNORED -> {
                    val fp = VcsUtil.getFilePath(absolutePath, entry.isDirectory)
                    builder.processIgnoredFile(fp)
                }

                GitFileStatus.ADDED -> {
                    // File was added to the index — no "before" content.
                    val after = RemoteGitWorkingCopyRevision(connectionManager, connectionName, absolutePath)
                    builder.processChange(
                        Change(null, after),
                        RemoteGitVcs.getKey()
                    )
                }

                GitFileStatus.DELETED -> {
                    // File was deleted — no "after" content, but we can show the old version.
                    val before = headHash?.let {
                        RemoteGitContentRevision(executor, repoPath, entry.relativePath, it)
                    }
                    builder.processChange(
                        Change(before, null),
                        RemoteGitVcs.getKey()
                    )
                }

                GitFileStatus.RENAMED -> {
                    val originalRelPath = entry.originalPath ?: entry.relativePath
                    val before = headHash?.let {
                        RemoteGitContentRevision(executor, repoPath, originalRelPath, it)
                    }
                    val after = RemoteGitWorkingCopyRevision(connectionManager, connectionName, absolutePath)
                    builder.processChange(
                        Change(before, after),
                        RemoteGitVcs.getKey()
                    )
                    // The Change object already records the rename (before -> after paths),
                    // so no additional processSwitchedFile call is needed.
                }

                GitFileStatus.MODIFIED,
                GitFileStatus.COPIED,
                GitFileStatus.UNMERGED -> {
                    val before = headHash?.let {
                        RemoteGitContentRevision(executor, repoPath, entry.relativePath, it)
                    }
                    val after = RemoteGitWorkingCopyRevision(connectionManager, connectionName, absolutePath)
                    builder.processChange(
                        Change(before, after),
                        RemoteGitVcs.getKey()
                    )
                }
            }
        }
    }

    /**
     * Remote Git status is driven by server-side polling; IntelliJ's document-change
     * tracking is not relevant.
     */
    override fun isModifiedDocumentTrackingRequired(): Boolean = false

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the full SHA-1 of HEAD, or `null` if the repository has no commits yet. */
    private fun resolveHead(): String? {
        return try {
            val result = executor.executeBlocking(repoPath, args = arrayOf("rev-parse", "HEAD"))
            if (result.isSuccess) result.stdout.trim().takeIf { it.length == 40 } else null
        } catch (_: Exception) {
            null
        }
    }
}
