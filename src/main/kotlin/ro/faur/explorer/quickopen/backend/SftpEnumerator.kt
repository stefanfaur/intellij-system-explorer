package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ro.faur.explorer.remote.SftpConnectionManager
import java.io.File

/**
 * Enumerates files over SFTP using a recursive directory listing.
 * Uses [SftpConnectionManager] to obtain an SFTP client for the given connection.
 *
 * The [connectionName] identifies the SSH connection profile, and [rootPath]
 * is the absolute remote path to enumerate (e.g., "/home/user/project").
 *
 * Ignores the same directories as [VfsEnumerator] (.git, node_modules, etc.)
 * to avoid scanning large irrelevant trees.
 */
class SftpEnumerator(
    private val connectionManager: SftpConnectionManager,
    private val connectionName: String,
    private val rootPath: String,
) : EnumeratorBackend {

    override val name = "SftpEnumerator"
    override fun isAvailable(): Boolean = connectionManager.isConnected(connectionName)

    private val ignoredDirNames = setOf(
        ".git", "node_modules", ".gradle", "build", "dist", ".idea",
        "out", "target", ".cache", "__pycache__", ".tox"
    )

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        val sftpClient = connectionManager.getSftpClient(connectionName)
            ?: run {
                LOG.warn("No SFTP client for connection: $connectionName")
                return@flow
            }

        var count = 0
        val queue = ArrayDeque<String>().apply { add(root) }

        while (queue.isNotEmpty() && count < maxResults) {
            val currentDir = queue.removeFirst()

            runCatching {
                val entries = sftpClient.readDir(currentDir)
                for (entry in entries) {
                    // Skip synthetic entries
                    if (entry.filename == "." || entry.filename == "..") continue

                    val fullPath = if (currentDir == "/") {
                        "/${entry.filename}"
                    } else {
                        "$currentDir/${entry.filename}"
                    }

                    if (entry.attributes.isDirectory) {
                        // Skip ignored directories
                        if (entry.filename !in ignoredDirNames) {
                            queue.add(fullPath)
                        }
                    } else {
                        emit(fullPath)
                        count++
                        if (count >= maxResults) return@flow
                    }
                }
            }.onFailure { e ->
                // Skip directories we can't read (permissions, etc.)
                LOG.debug("Cannot read directory: $currentDir", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val LOG = Logger.getInstance(SftpEnumerator::class.java)
    }
}
