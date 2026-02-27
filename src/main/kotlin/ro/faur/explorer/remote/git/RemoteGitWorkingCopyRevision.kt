package ro.faur.explorer.remote.git

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import ro.faur.explorer.remote.SftpConnectionManager
import java.nio.charset.StandardCharsets

/**
 * A [ContentRevision] that represents the current working-copy state of a remote file.
 *
 * The file is downloaded via SFTP to a temporary local file, read into a string, and
 * then the temporary file is deleted. Because this is the working copy there is no
 * meaningful git revision; [getRevisionNumber] therefore returns [VcsRevisionNumber.NULL].
 *
 * @param connectionManager Manager that provides access to the cached [SftpClient].
 * @param connectionName    Key identifying the remote connection inside [connectionManager].
 * @param remotePath        Absolute path to the file on the remote host.
 */
class RemoteGitWorkingCopyRevision(
    private val connectionManager: SftpConnectionManager,
    private val connectionName: String,
    private val remotePath: String,
) : ContentRevision {

    private val filePath: FilePath by lazy {
        VcsUtil.getFilePath(remotePath, false)
    }

    /**
     * Downloads the remote file and returns its content as a UTF-8 string.
     *
     * Returns `null` when:
     * - the remote connection is unavailable, or
     * - the file does not exist on the remote host.
     *
     * @throws VcsException if the download or read fails unexpectedly.
     */
    override fun getContent(): String? {
        val sftpClient = connectionManager.getSftpClient(connectionName)
            ?: return null  // not connected — treat as unavailable

        return try {
            sftpClient.read(remotePath).use { remoteIn ->
                remoteIn.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            // File may not exist (deleted in working copy) — return null rather than throwing.
            if (isFileNotFoundError(e)) null
            else throw VcsException("Failed to read working-copy content of $remotePath: ${e.message}", e)
        }
    }

    override fun getFile(): FilePath = filePath

    /** The working copy has no specific revision number. */
    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isFileNotFoundError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("no such file") || msg.contains("not found") || msg.contains("does not exist")
    }
}
