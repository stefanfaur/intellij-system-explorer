package ro.faur.explorer.remote.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import ro.faur.explorer.remote.SftpConnectionManager

/**
 * The IntelliJ VCS abstraction for "Remote Git" — a read-only git integration
 * that operates entirely over SSH/SFTP rather than the local file system.
 *
 * Providers are initialised lazily via [configure]; until [configure] is
 * called, all `get*Provider()` methods return `null` and the VCS behaves as
 * an unregistered stub. This separation is necessary because the
 * [SftpConnectionManager], connection name, and repository path are only known
 * after the user connects to a remote host, which happens after IntelliJ has
 * already instantiated this class as part of the VCS framework setup.
 *
 * Typical lifecycle:
 * ```
 * val vcs = RemoteGitVcs(project)
 * // … user connects …
 * vcs.configure(connectionManager, "myServer", "/home/user/myRepo")
 * ```
 */
class RemoteGitVcs(project: Project) : AbstractVcs(project, NAME) {

    // ── Mutable provider state ─────────────────────────────────────────────────

    @Volatile private var changeProvider: RemoteGitChangeProvider? = null
    @Volatile private var diffProvider: RemoteGitDiffProvider? = null
    @Volatile private var historyProvider: RemoteGitHistoryProvider? = null
    @Volatile private var annotationProvider: RemoteGitAnnotationProvider? = null

    // ── AbstractVcs overrides ─────────────────────────────────────────────────

    override fun getDisplayName(): String = "Remote Git"

    override fun getChangeProvider(): ChangeProvider? = changeProvider

    override fun getDiffProvider(): DiffProvider? = diffProvider

    override fun getVcsHistoryProvider(): VcsHistoryProvider? = historyProvider

    override fun getAnnotationProvider(): AnnotationProvider? = annotationProvider

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Initialises all VCS providers for the given remote connection and repository.
     *
     * This method is idempotent — calling it again with different parameters
     * replaces the existing providers atomically.
     *
     * @param connectionManager Manages the SSH/SFTP connection lifecycle.
     * @param connectionName    Key used to look up the connection in [connectionManager].
     * @param repoPath          Absolute path to the git repository root on the remote host.
     */
    fun configure(
        connectionManager: SftpConnectionManager,
        connectionName: String,
        repoPath: String,
    ) {
        val executor = RemoteGitCommandExecutor(connectionManager, connectionName)

        changeProvider    = RemoteGitChangeProvider(executor, connectionName, repoPath, connectionManager)
        diffProvider      = RemoteGitDiffProvider(executor, connectionName, repoPath, connectionManager)
        historyProvider   = RemoteGitHistoryProvider(executor, connectionName, repoPath)
        annotationProvider = RemoteGitAnnotationProvider(project, executor, connectionName, repoPath)
    }

    /**
     * Removes all provider references, effectively reverting the VCS to its
     * uninitialised stub state.  Call this when the user disconnects.
     */
    fun deconfigure() {
        changeProvider     = null
        diffProvider       = null
        historyProvider    = null
        annotationProvider = null
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val NAME = "RemoteGit"

        private val KEY = createKey(NAME)

        fun getKey(): VcsKey = KEY

        fun getInstance(project: Project): RemoteGitVcs {
            return ProjectLevelVcsManager.getInstance(project)
                .findVcsByName(NAME) as RemoteGitVcs
        }
    }
}
