package ro.faur.explorer.gitpanel.exec

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

class LocalGitCommandExecutor : GitCommandExecutor {

    override fun executeBlocking(
        repoPath: String,
        timeout: Duration,
        vararg args: String
    ): GitCommandResult {
        val command = mutableListOf("git") + args.toList()
        val process = ProcessBuilder(command)
            .directory(File(repoPath))
            .redirectErrorStream(false)
            .start()

        var stdoutBytes = ByteArray(0)
        var stderrBytes = ByteArray(0)
        val stdoutThread = Thread { stdoutBytes = process.inputStream.readBytes() }.also { it.isDaemon = true; it.start() }
        val stderrThread = Thread { stderrBytes = process.errorStream.readBytes() }.also { it.isDaemon = true; it.start() }

        val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        stdoutThread.join(1_000)
        stderrThread.join(1_000)

        return GitCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = String(stdoutBytes),
            stderr = String(stderrBytes),
        )
    }
}
