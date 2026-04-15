package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.sshd.client.channel.ClientChannelEvent
import ro.faur.explorer.remote.SftpConnectionManager
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.EnumSet

/**
 * Enumerates files over SSH using the `find` command via exec channel.
 * Much faster than SFTP recursive listing since it runs a single command.
 *
 * The [connectionName] identifies the SSH connection profile, and [rootPath]
 * is the absolute remote path to enumerate (e.g., "/home/user/project").
 *
 * Ignores the same directories as [VfsEnumerator] (.git, node_modules, etc.)
 * to avoid scanning large irrelevant trees.
 */
class SshFindEnumerator(
    private val connectionManager: SftpConnectionManager,
    private val connectionName: String,
    private val rootPath: String,
) : EnumeratorBackend {

    override val name = "SshFindEnumerator"
    override fun isAvailable(): Boolean = connectionManager.isConnected(connectionName)

    private val ignoredDirNames = setOf(
        ".git", "node_modules", ".gradle", "build", "dist", ".idea",
        "out", "target", ".cache", "__pycache__", ".tox"
    )

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        val session = connectionManager.getSession(connectionName)
            ?: run {
                LOG.warn("No SSH session for connection: $connectionName")
                return@flow
            }

        val findCommand = buildFindCommand(root)
        LOG.debug("Executing: $findCommand")

        val channel = session.createExecChannel(findCommand)
        val stdout = ByteArrayOutputStream()
        channel.out = stdout

        try {
            channel.open().verify(DEFAULT_TIMEOUT_MS)
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), DEFAULT_TIMEOUT_MS)

            val output = stdout.toString(StandardCharsets.UTF_8)
            val lines = output.lineSequence()
            var count = 0
            for (line in lines) {
                if (count >= maxResults) break
                emit(line)
                count++
            }

            LOG.debug("SshFindEnumerator finished: $count files")
        } catch (e: Exception) {
            LOG.warn("SSH find failed for $root: ${e.message}")
        } finally {
            channel.close(false)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildFindCommand(root: String): String {
        val escapedRoot = shellEscape(root)
        val pruneConditions = ignoredDirNames.joinToString(" -o ") { name ->
            "-path ${shellEscape("*/$name/*")}"
        }
        // find ... -not \( ... \) -type f
        // -L follows symlinks (like VfsEnumerator File.walkTopDown)
        // -type f only returns files (not directories)
        return "find -L $escapedRoot -not \\( $pruneConditions \\) -type f"
    }

    companion object {
        private val LOG = Logger.getInstance(SshFindEnumerator::class.java)
        private const val DEFAULT_TIMEOUT_MS = 60_000L // 60 seconds

        /** POSIX single-quote shell escape. */
        private fun shellEscape(value: String): String {
            if (value.isEmpty()) return "''"
            return "'" + value.replace("'", "'\\''") + "'"
        }
    }
}
