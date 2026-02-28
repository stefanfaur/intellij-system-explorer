package ro.faur.explorer.gitpanel

import com.intellij.openapi.diagnostic.Logger
import ro.faur.explorer.gitpanel.exec.GitCommandResult
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

    override fun getHeadContent(path: String): ByteArray? {
        // Remote backend: reads from git index (staging area) as a proxy for working-tree.
        // Unstaged local edits on remote won't show in diff — known limitation.
        val result = executor.executeBlocking(repoPath, args = arrayOf("show", ":$path"))
        return if (result.isSuccess) result.stdout.toByteArray(Charsets.UTF_8) else null
    }

    override fun getFileAtRevision(hash: String, path: String): ByteArray? {
        val result = executor.executeBlocking(repoPath, args = arrayOf("show", "$hash:$path"))
        return if (result.isSuccess) result.stdout.toByteArray(Charsets.UTF_8) else null
    }

    override fun stageFiles(paths: List<String>): GitCommandResult {
        if (paths.isEmpty()) return GitCommandResult(0, "", "")
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("add", "--") + paths.toTypedArray()
        )
    }

    override fun commit(message: String): GitCommandResult {
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("commit", "-m", message)
        )
    }

    override fun push(): GitCommandResult {
        return executor.executeBlocking(
            repoPath,
            args = arrayOf("push")
        )
    }

    override fun pull(): GitCommandResult {
        val result = executor.executeBlocking(repoPath, args = arrayOf("pull"))
        if (!result.isSuccess && result.stderr.isNotBlank()) {
            LOG.warn("git pull failed for $displayName (exit ${result.exitCode}): ${result.stderr.take(300)}")
        }
        return result
    }

    override fun listBranches(): List<BranchInfo> {
        val result = executor.executeBlocking(
            repoPath,
            args = arrayOf("branch", "--format=%(refname:short)\t%(HEAD)")
        )
        if (!result.isSuccess) {
            if (result.stderr.isNotBlank()) LOG.warn("git branch failed for $displayName: ${result.stderr.take(300)}")
            return emptyList()
        }
        return result.stdoutLines.mapNotNull { parseBranchLine(it) }
    }

    override fun checkoutBranch(name: String): GitCommandResult {
        return executor.executeBlocking(repoPath, args = arrayOf("checkout", name))
    }

    override fun createBranch(name: String): GitCommandResult {
        return executor.executeBlocking(repoPath, args = arrayOf("checkout", "-b", name))
    }

    override fun deleteBranch(name: String, force: Boolean): GitCommandResult {
        val flag = if (force) "-D" else "-d"
        return executor.executeBlocking(repoPath, args = arrayOf("branch", flag, name))
    }

    override fun stash(message: String?, includeUntracked: Boolean): GitCommandResult {
        val args = mutableListOf("stash", "push")
        if (includeUntracked) args.add("--include-untracked")
        if (message != null) { args.add("-m"); args.add(message) }
        return executor.executeBlocking(repoPath, args = args.toTypedArray())
    }

    override fun stashList(): List<StashEntry> {
        val result = executor.executeBlocking(
            repoPath,
            args = arrayOf("stash", "list", "--format=%gd\t%s")
        )
        if (!result.isSuccess) {
            if (result.stderr.isNotBlank()) LOG.warn("git stash list failed for $displayName: ${result.stderr.take(300)}")
            return emptyList()
        }
        return result.stdoutLines.mapNotNull { parseStashLine(it) }
    }

    override fun stashPop(index: Int): GitCommandResult {
        return executor.executeBlocking(repoPath, args = arrayOf("stash", "pop", "stash@{$index}"))
    }

    override fun dispose() {}
}
