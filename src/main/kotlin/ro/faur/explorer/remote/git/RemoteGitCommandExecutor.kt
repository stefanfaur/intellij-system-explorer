package ro.faur.explorer.remote.git

import org.apache.sshd.client.channel.ClientChannelEvent
import ro.faur.explorer.remote.SftpConnectionManager
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.EnumSet

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
    val stdoutLines: List<String> get() = stdout.lines().filter { it.isNotEmpty() }
}

class RemoteGitCommandExecutor(
    private val connectionManager: SftpConnectionManager,
    private val connectionName: String
) {
    fun executeBlocking(
        repoPath: String,
        timeout: Duration = Duration.ofSeconds(30),
        vararg args: String
    ): GitCommandResult {
        val session = connectionManager.getSession(connectionName)
            ?: throw IllegalStateException("Not connected to $connectionName")

        val escapedPath = shellEscape(repoPath)
        val escapedArgs = args.joinToString(" ") { shellEscape(it) }
        val command = "cd $escapedPath && git $escapedArgs"

        val channel = session.createExecChannel(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.out = stdout
        channel.err = stderr
        channel.open().verify(timeout.toMillis())
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis())

        return GitCommandResult(
            exitCode = channel.exitStatus ?: -1,
            stdout = stdout.toString(StandardCharsets.UTF_8),
            stderr = stderr.toString(StandardCharsets.UTF_8)
        )
    }

    fun executeLinesBlocking(
        repoPath: String,
        timeout: Duration = Duration.ofSeconds(30),
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
        val command = "cd $escapedPath && git $escapedArgs"

        val channel = session.createExecChannel(command)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.out = stdout
        channel.err = stderr
        channel.open().verify(timeout.toMillis())
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis())

        return stdout.toByteArray()
    }

    companion object {
        /** POSIX single-quote shell escape — prevents command injection. */
        fun shellEscape(value: String): String {
            if (value.isEmpty()) return "''"
            return "'" + value.replace("'", "'\\''") + "'"
        }
    }
}
