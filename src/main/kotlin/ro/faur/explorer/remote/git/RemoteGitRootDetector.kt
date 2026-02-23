package ro.faur.explorer.remote.git

import com.intellij.openapi.project.Project
import ro.faur.explorer.remote.SftpEntry

/**
 * Detects git repository roots in remote directory listings.
 *
 * A directory is considered to be a git repository root when its contents
 * include an entry named `.git` that is itself a directory (or, for git
 * worktrees and submodules, a regular file — both cases are handled).
 *
 * This class is intentionally small and stateless. It is called from
 * `RemoteTreePanel` after every directory listing so that newly discovered
 * repositories are registered with [RemoteGitVcsManager] and can immediately
 * participate in status colouring and history browsing.
 *
 * @param vcsManager The project-scoped manager that tracks known remote roots.
 */
class RemoteGitRootDetector(private val vcsManager: RemoteGitVcsManager) {

    /**
     * Inspects [entries] (the contents of [directoryPath]) and, if a `.git`
     * entry is found, registers [directoryPath] as a git repository root for
     * [connectionName].
     *
     * If the directory was already registered, this call is a no-op.
     *
     * @param connectionName  Key identifying the remote SSH connection.
     * @param directoryPath   Absolute path of the directory whose entries are supplied.
     * @param entries         Directory listing returned by the SFTP client.
     */
    fun detectAndRegister(
        connectionName: String,
        directoryPath: String,
        entries: List<SftpEntry>,
    ) {
        if (isGitRoot(entries)) {
            vcsManager.registerRoot(connectionName, directoryPath)
        }
    }

    /**
     * Returns `true` when [entries] contains a `.git` entry.
     *
     * Both the standard case (`.git` is a directory) and the worktree/submodule
     * case (`.git` is a file that contains a `gitdir:` reference) are accepted,
     * because from the SFTP listing perspective both look like regular entries.
     */
    fun isGitRoot(entries: List<SftpEntry>): Boolean =
        entries.any { it.name == ".git" }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        /**
         * Convenience factory that resolves [RemoteGitVcsManager] from [project]
         * and constructs a [RemoteGitRootDetector].
         */
        fun forProject(project: Project): RemoteGitRootDetector =
            RemoteGitRootDetector(RemoteGitVcsManager.getInstance(project))
    }
}
