package ro.faur.explorer.gitpanel

import ro.faur.explorer.gitpanel.exec.GitCommandResult
import ro.faur.explorer.gitpanel.exec.LocalGitCommandExecutor
import ro.faur.explorer.remote.git.GitFileStatus
import ro.faur.explorer.remote.git.GitLogEntry
import ro.faur.explorer.remote.git.GitLogParser
import ro.faur.explorer.remote.git.GitStatusParser
import java.time.Instant

class LocalGitBackend(override val repoPath: String) : GitBackend {

    override val id: GitBackendId = GitBackendId(BackendType.LOCAL, null, repoPath)

    override val displayName: String = run {
        val parts = repoPath.trimEnd('/').split("/")
        if (parts.size >= 2) "${parts[parts.size - 2]}/${parts.last()}" else parts.last()
    }

    private val executor = LocalGitCommandExecutor()

    override fun getCurrentBranch(): String? {
        val result = executor.executeBlocking(repoPath, args = arrayOf("rev-parse", "--abbrev-ref", "HEAD"))
        return if (result.isSuccess) result.stdout.trim().takeIf { it.isNotEmpty() } else null
    }

    override fun getLog(maxCount: Int): List<GitLogEntry> {
        val lines = executor.executeLinesBlocking(
            repoPath,
            args = arrayOf("log", "--format=%H|%an|%ae|%at|%s", "-n", "$maxCount")
        )
        return GitLogParser.parse(lines)
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
        val result = executor.executeBlocking(repoPath, args = arrayOf("show", "HEAD:$path"))
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
        return executor.executeBlocking(repoPath, args = arrayOf("pull"))
    }

    override fun listBranches(): List<BranchInfo> {
        val result = executor.executeBlocking(
            repoPath,
            args = arrayOf("branch", "--format=%(refname:short)\t%(HEAD)")
        )
        if (!result.isSuccess) return emptyList()
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
        if (!result.isSuccess) return emptyList()
        return result.stdoutLines.mapNotNull { parseStashLine(it) }
    }

    override fun stashPop(index: Int): GitCommandResult {
        return executor.executeBlocking(repoPath, args = arrayOf("stash", "pop", "stash@{$index}"))
    }

    override fun resetHard(): GitCommandResult =
        executor.executeBlocking(repoPath, args = arrayOf("reset", "--hard", "HEAD"))

    override fun dispose() {}
}

internal fun parseBranchLine(line: String): BranchInfo? {
    val parts = line.split("\t")
    if (parts.size < 2) return null
    return BranchInfo(name = parts[0].trim(), isCurrent = parts[1].trim() == "*")
}

internal fun parseStashLine(line: String): StashEntry? {
    val tabIdx = line.indexOf('\t')
    if (tabIdx < 0) return null
    val ref = line.substring(0, tabIdx)  // "stash@{0}"
    val msg = line.substring(tabIdx + 1)
    val index = ref.removePrefix("stash@{").removeSuffix("}").toIntOrNull() ?: return null
    return StashEntry(index = index, message = msg)
}

internal fun parseDiffTreeLine(line: String): CommitFile? {
    if (line.isBlank()) return null
    return when {
        line.startsWith("R") || line.startsWith("C") -> {
            // R100\told/path\tnew/path
            val parts = line.split("\t")
            if (parts.size < 3) return null
            val status = if (line.startsWith("R")) GitFileStatus.RENAMED else GitFileStatus.COPIED
            CommitFile(parts[2], status, parts[1])
        }
        else -> {
            val parts = line.split("\t")
            if (parts.size < 2) return null
            val status = when (parts[0].firstOrNull()) {
                'M' -> GitFileStatus.MODIFIED
                'A' -> GitFileStatus.ADDED
                'D' -> GitFileStatus.DELETED
                else -> return null
            }
            CommitFile(parts[1], status)
        }
    }
}

internal fun parseCommitInfo(stdout: String): CommitInfo? {
    val lines = stdout.lines()
    if (lines.size < 5) return null
    val hash = lines[0].trim()
    val author = lines[1].trim()
    val email = lines[2].trim()
    val epochSeconds = lines[3].trim().toLongOrNull() ?: return null
    val subject = lines[4].trim()
    val body = lines.drop(5).joinToString("\n").trimEnd()
    return CommitInfo(
        hash = hash,
        author = author,
        email = email,
        date = Instant.ofEpochSecond(epochSeconds),
        subject = subject,
        body = body,
    )
}
