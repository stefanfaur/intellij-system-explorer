package ro.faur.explorer.gitpanel

import com.intellij.openapi.diagnostic.Logger
import ro.faur.explorer.remote.git.GitLogEntry
import ro.faur.explorer.remote.git.GitLogParser
import ro.faur.explorer.remote.git.GitStatusParser
import ro.faur.explorer.remote.git.RemoteGitCommandExecutor

class RemoteGitBackend(
    val connectionName: String,
    override val repoPath: String,
    private val executor: RemoteGitCommandExecutor,
) : GitBackend {

    companion object {
        private val LOG = Logger.getInstance(RemoteGitBackend::class.java)
    }

    override val id: GitBackendId = GitBackendId(BackendType.REMOTE, connectionName, repoPath)

    override val displayName: String =
        "$connectionName:${repoPath.trimEnd('/').substringAfterLast('/')}"

    override fun getCurrentBranch(): String? {
        val result = executor.executeBlocking(repoPath, args = arrayOf("rev-parse", "--abbrev-ref", "HEAD"))
        return if (result.isSuccess) result.stdout.trim().takeIf { it.isNotEmpty() } else null
    }

    override fun getLog(maxCount: Int): List<GitLogEntry> {
        val result = executor.executeBlocking(
            repoPath,
            args = arrayOf("log", "--format=%H|%an|%ae|%at|%s", "-n", "$maxCount")
        )
        if (!result.isSuccess && result.stderr.isNotBlank()) {
            LOG.warn("git log failed for $displayName (exit ${result.exitCode}): ${result.stderr.take(300)}")
        }
        return GitLogParser.parse(result.stdoutLines)
    }

    override fun getWorkingTreeStatus(): List<CommitFile> {
        val lines = executor.executeLinesBlocking(
            repoPath,
            args = arrayOf("status", "--porcelain=v2", "--untracked-files=all")
        )
        return lines.mapNotNull { GitStatusParser.parseLine(it) }.map { entry ->
            CommitFile(entry.relativePath, entry.status, entry.originalPath)
        }
    }

    override fun getCommitFiles(hash: String): List<CommitFile> {
        val lines = executor.executeLinesBlocking(
            repoPath,
            args = arrayOf("diff-tree", "--no-commit-id", "-r", "--name-status", hash)
        )
        return lines.mapNotNull { parseDiffTreeLine(it) }
    }

    override fun getCommitInfo(hash: String): CommitInfo? {
        val result = executor.executeBlocking(
            repoPath,
            args = arrayOf("show", "-s", "--format=%H%n%an%n%ae%n%at%n%s%n%b", hash)
        )
        if (!result.isSuccess) return null
        return parseCommitInfo(result.stdout)
    }

    override fun stageFiles(paths: List<String>): ro.faur.explorer.gitpanel.exec.GitCommandResult {
        if (paths.isEmpty()) return ro.faur.explorer.gitpanel.exec.GitCommandResult(0, "", "")
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("add", "--") + paths.toTypedArray()
        )
    }

    override fun commit(message: String): ro.faur.explorer.gitpanel.exec.GitCommandResult {
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("commit", "-m", message)
        )
    }

    override fun push(): ro.faur.explorer.gitpanel.exec.GitCommandResult {
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("push")
        )
    }

    override fun dispose() {}
}
