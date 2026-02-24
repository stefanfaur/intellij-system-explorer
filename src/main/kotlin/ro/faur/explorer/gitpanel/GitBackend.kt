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

interface GitBackend : Disposable {
    val id: GitBackendId
    val displayName: String
    val repoPath: String
    fun getCurrentBranch(): String?
    fun getLog(maxCount: Int = 100): List<GitLogEntry>
    fun getWorkingTreeStatus(): List<CommitFile>
    fun getCommitFiles(hash: String): List<CommitFile>
    fun getCommitInfo(hash: String): CommitInfo?
    fun stageFiles(paths: List<String>): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun commit(message: String): ro.faur.explorer.gitpanel.exec.GitCommandResult
    fun push(): ro.faur.explorer.gitpanel.exec.GitCommandResult
    override fun dispose()
}
