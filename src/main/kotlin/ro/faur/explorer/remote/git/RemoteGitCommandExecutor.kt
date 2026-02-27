package ro.faur.explorer.remote.git

import com.intellij.openapi.diagnostic.Logger
import org.apache.sshd.client.channel.ClientChannelEvent
import ro.faur.explorer.gitpanel.exec.GitCommandExecutor
import ro.faur.explorer.remote.SftpConnectionManager
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.EnumSet

// Re-export under the old name so callers in this package continue to compile
typealias GitCommandResult = ro.faur.explorer.gitpanel.exec.GitCommandResult

class RemoteGitCommandExecutor(
    private val connectionManager: SftpConnectionManager,
    private val connectionName: String
) : GitCommandExecutor {
    override fun executeBlocking(
        repoPath: String,
        timeout: Duration,
        vararg args: String
    ): GitCommandResult {
        val session = connectionManager.getSession(connectionName)
            ?: throw IllegalStateException("Not connected to $connectionName")

        val escapedPath = shellEscape(repoPath)
        val escapedArgs = args.joinToString(" ") { shellEscape(it) }
        // GIT_PAGER=cat prevents git from spawning a pager (e.g. less) which would
        // hang waiting for input on a non-TTY SSH exec channel.
        // GIT_TERMINAL_PROMPT=0 prevents git from prompting for credentials on a
        // non-interactive channel, which would also hang indefinitely.
        // -c safe.directory=<path> bypasses the git 2.35.2+ "dubious ownership" check
        // (exit 128) that fires when the repo is owned by a different OS user than the
        // one running the git command — a common pattern when connecting as e.g. "deploy"
        // to a repo owned by "app" or "tomcat".
        val safeDirFlag = "-c ${shellEscape("safe.directory=$repoPath")}"
        val command = "cd $escapedPath && GIT_PAGER=cat GIT_TERMINAL_PROMPT=0 git $safeDirFlag $escapedArgs"

        val channel = session.createExecChannel(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.out = stdout
        channel.err = stderr
        return try {
            channel.open().verify(timeout.toMillis())
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis())
            val result = GitCommandResult(
                exitCode = channel.exitStatus ?: -1,
                stdout = stdout.toString(StandardCharsets.UTF_8),
                stderr = stderr.toString(StandardCharsets.UTF_8)
            )
            if (!result.isSuccess && result.stderr.isNotBlank()) {
                LOG.warn(
                    "Remote git command failed on $connectionName " +
                        "(exit ${result.exitCode}): $command\nstderr: ${result.stderr.take(500)}"
                )
            }
            result
        } finally {
            channel.close(false)
        }
    }

    override fun executeLinesBlocking(
        repoPath: String,
        timeout: Duration,
        vararg args: String
    ): List<String> = executeBlocking(repoPath, timeout, *args).stdoutLines

    fun executeBytesBlocking(
        repoPath: String,
        timeout: Duration = Duration.ofSeconds(30),
        vararg args: String
    ): ByteArray {
        val session = connectionManager.getSession(connectionName)
            ?: throw IllegalStateException("Not connected to $connectionName")

        val escapedPath = shellEscape(repoPath)
        val escapedArgs = args.joinToString(" ") { shellEscape(it) }
        val safeDirFlag = "-c ${shellEscape("safe.directory=$repoPath")}"
        val command = "cd $escapedPath && GIT_PAGER=cat GIT_TERMINAL_PROMPT=0 git $safeDirFlag $escapedArgs"

        val channel = session.createExecChannel(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.out = stdout
        channel.err = stderr
        return try {
            channel.open().verify(timeout.toMillis())
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis())
            stdout.toByteArray()
        } finally {
            channel.close(false)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(RemoteGitCommandExecutor::class.java)

        /** POSIX single-quote shell escape — prevents command injection. */
        fun shellEscape(value: String): String {
            if (value.isEmpty()) return "''"
            return "'" + value.replace("'", "'\\''") + "'"
        }
    }
}
