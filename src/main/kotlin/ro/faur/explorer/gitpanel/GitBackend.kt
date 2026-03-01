package ro.faur.explorer.gitpanel

import com.intellij.openapi.Disposable
import ro.faur.explorer.remote.git.GitFileStatus
import ro.faur.explorer.remote.git.GitLogEntry
import java.time.Instant

enum class BackendType { LOCAL, REMOTE }

data class GitBackendId(
    val type: BackendType,
    val connectionName: String?,  // null for LOCAL
    val repoPath: String,
) {
    val key: String get() = when (type) {
        BackendType.LOCAL -> "local:$repoPath"
        BackendType.REMOTE -> "remote:$connectionName:$repoPath"
    }
}

data class CommitFile(
    val path: String,
    val status: GitFileStatus,
    val oldPath: String? = null,
)

data class CommitInfo(
    val hash: String,
    val author: String,
    val email: String,
    val date: Instant,
    val subject: String,
    val body: String,
)

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
)

data class StashEntry(
    val index: Int,        // 0-based, matches stash@{N}
    val message: String,   // "WIP on branch: <hash> <subject>" or custom message
)

interface GitBackend : Disposable {
    val id: GitBackendId
    val displayName: String
    val repoPath: String
    fun getCurrentBranch(): String?
    fun getLog(maxCount: Int = 100): List<GitLogEntry>
    fun getWorkingTreeStatus(): List<CommitFile>
    fun getCommitFiles(hash: String): List<CommitFile>
    fun getCommitInfo(hash: String): CommitInfo?
    fun getHeadContent(path: String): ByteArray?
    fun getFileAtRevision(hash: String, path: String): ByteArray?
    fun stageFiles(paths: List<String>): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun commit(message: String): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun push(): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun pull(): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun listBranches(): List<BranchInfo>
    fun checkoutBranch(name: String): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun createBranch(name: String): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun deleteBranch(name: String, force: Boolean = false): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun stash(message: String?, includeUntracked: Boolean): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun stashList(): List<StashEntry>
    fun stashPop(index: Int): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun stashApply(index: Int): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun stashDrop(index: Int): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun resetHard(): ro.faur.explorer.gitpanel.exec.GitCommandResult
    override fun dispose()
}
