package ro.faur.explorer.gitpanel.exec

import java.time.Duration

interface GitCommandExecutor {
    fun executeBlocking(
        repoPath: String,
        timeout: Duration = Duration.ofSeconds(30),
        vararg args: String,
    ): GitCommandResult

    fun executeLinesBlocking(
        repoPath: String,
        timeout: Duration = Duration.ofSeconds(30),
        vararg args: String,
    ): List<String> = executeBlocking(repoPath, timeout, *args).stdoutLines
}
