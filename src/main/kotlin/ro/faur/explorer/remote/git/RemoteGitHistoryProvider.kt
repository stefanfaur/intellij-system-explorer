package ro.faur.explorer.remote.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsDependentHistoryComponents
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import java.util.Date
import javax.swing.JComponent

/**
 * Implements [VcsHistoryProvider] for the Remote Git VCS integration.
 *
 * When the user invokes "Show History" on a remote file, IntelliJ calls
 * [createSessionFor] with the file's [FilePath]. This implementation runs
 * `git log` on the remote host and returns a [VcsHistorySession] populated
 * with [RemoteGitFileRevision] entries.
 *
 * @param executor       Executes git commands on the remote host.
 * @param connectionName Key identifying the remote connection.
 * @param repoPath       Absolute path to the git repository root on the remote.
 */
class RemoteGitHistoryProvider(
    private val executor: RemoteGitCommandExecutor,
    private val connectionName: String,
    private val repoPath: String,
) : VcsHistoryProvider {

    /**
     * Builds a full history session for [filePath].
     *
     * The git log format string `%H|%an|%ae|%at|%s` yields pipe-delimited fields:
     * commit hash, author name, author e-mail, author timestamp (Unix epoch seconds),
     * and commit subject (first line of the message).
     *
     * @throws VcsException if the remote command fails.
     */
    @Throws(VcsException::class)
    override fun createSessionFor(filePath: FilePath): VcsHistorySession {
        val relPath = relativize(filePath.path)
            ?: throw VcsException("File ${filePath.path} is outside repository $repoPath")

        val revisions = fetchRevisions(relPath)
        val currentRevision = revisions.firstOrNull()?.getRevisionNumber()

        return object : VcsAbstractHistorySession(revisions) {
            override fun calcCurrentRevisionNumber(): VcsRevisionNumber? = currentRevision
            override fun getHistoryAsTreeProvider(): HistoryAsTreeProvider? = null
            override fun copy(): VcsAbstractHistorySession =
                createSessionFor(filePath) as VcsAbstractHistorySession
        }
    }

    /**
     * Streams history incrementally. The default implementation delegates to
     * [createSessionFor] and reports all revisions at once.
     */
    @Throws(VcsException::class)
    override fun reportAppendableHistory(
        path: FilePath,
        partner: VcsAppendableHistorySessionPartner,
    ) {
        try {
            val session = createSessionFor(path)
            partner.reportCreatedEmptySession(session as VcsAbstractHistorySession)
            session.revisionList.forEach { partner.acceptRevision(it) }
        } catch (e: VcsException) {
            partner.reportException(e)
        }
    }

    override fun supportsHistoryForDirectories(): Boolean = false
    override fun isDateOmittable(): Boolean = false
    override fun getHelpId(): String? = null
    override fun getAdditionalActions(refresher: Runnable?): Array<AnAction> = emptyArray()

    override fun getUICustomization(session: VcsHistorySession, provider: JComponent): VcsDependentHistoryComponents =
        VcsDependentHistoryComponents.createOnlyColumns(emptyArray())

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(file: VirtualFile): Boolean = true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun relativize(path: String): String? {
        if (path == repoPath) return "."
        val prefix = "$repoPath/"
        return if (path.startsWith(prefix)) path.removePrefix(prefix) else null
    }

    @Throws(VcsException::class)
    private fun fetchRevisions(relPath: String): List<VcsFileRevision> {
        val result = try {
            executor.executeBlocking(
                repoPath,
                args = arrayOf("log", "--format=%H|%an|%ae|%at|%s", "--", relPath)
            )
        } catch (e: IllegalStateException) {
            throw VcsException("git log failed for $relPath: ${e.message}", e)
        }

        if (!result.isSuccess) {
            throw VcsException("git log returned exit ${result.exitCode} for $relPath: ${result.stderr.trim()}")
        }

        return GitLogParser.parse(result.stdoutLines).map { entry ->
            RemoteGitFileRevision(
                executor = executor,
                repoPath = repoPath,
                relPath = relPath,
                hash = entry.hash,
                authorName = entry.authorName,
                authorEmail = entry.authorEmail,
                revisionDate = entry.date,
                commitMessage = entry.subject,
            )
        }
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    /**
     * A single entry in the file's git history.
     *
     * Extends [VcsFileRevisionEx] to expose author e-mail alongside author name,
     * which IntelliJ displays in the History panel.
     */
    private class RemoteGitFileRevision(
        private val executor: RemoteGitCommandExecutor,
        private val repoPath: String,
        private val relPath: String,
        private val hash: String,
        private val authorName: String,
        private val authorEmail: String,
        private val revisionDate: Date,
        private val commitMessage: String,
    ) : VcsFileRevisionEx() {

        private val revisionNumber = RemoteGitRevisionNumber(hash)

        override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber
        override fun getBranchName(): String? = null
        override fun getRevisionDate(): Date = revisionDate
        override fun getAuthor(): String = authorName
        override fun getCommitMessage(): String = commitMessage

        // VcsFileRevisionEx abstract methods
        override fun getAuthorEmail(): String = authorEmail
        override fun getCommitterName(): String? = null
        override fun getCommitterEmail(): String? = null
        override fun getAuthorDate(): Date = revisionDate
        override fun isDeleted(): Boolean = false

        // VcsFileRevision abstract method
        override fun getChangedRepositoryPath(): RepositoryLocation? = null

        /**
         * Returns the [FilePath] for this revision. Uses [getPath] as required by
         * [VcsFileRevisionEx].
         */
        override fun getPath(): FilePath = VcsUtil.getFilePath("$repoPath/$relPath", false)

        /**
         * Fetches the file content at this revision via `git show hash:path`.
         */
        @Throws(VcsException::class)
        override fun loadContent(): ByteArray {
            return try {
                executor.executeBytesBlocking(
                    repoPath,
                    args = arrayOf("show", "$hash:$relPath")
                )
            } catch (e: Exception) {
                throw VcsException("Failed to load content for $relPath at $hash: ${e.message}", e)
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getContent(): ByteArray? = runCatching { loadContent() }.getOrNull()
    }
}
