package ro.faur.explorer.remote

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.sftp.client.SftpClient
import ro.faur.explorer.remote.security.RemoteAuditLogger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Bridges local VirtualFile operations with remote SFTP operations
 * for cross-panel drag-and-drop and clipboard transfers.
 *
 * Features:
 * - Recursive directory upload/download
 * - Per-file error collection — one failure does not abort the entire batch
 * - Cache invalidation of the target remote directory after upload
 * - Audit logging for every transferred file
 */
class CrossPanelTransferService(
    private val project: Project,
    private val connectionManager: SftpConnectionManager,
    private val directoryCache: DirectoryCache? = null,
) {

    /**
     * Uploads local files (and directories recursively) to [remoteTargetDir] on the remote host.
     *
     * Progress is reported via IntelliJ's background progress mechanism.
     * Per-file errors are collected and thrown as [BatchTransferException] at the end.
     *
     * @throws BatchTransferException if one or more files failed to upload
     * @throws IllegalStateException if not connected to [connectionName]
     */
    suspend fun uploadAsync(
        localFiles: List<VirtualFile>,
        remoteTargetDir: String,
        connectionName: String,
    ) {
        val sftpClient = connectionManager.getSftpClient(connectionName)
            ?: throw IllegalStateException("Not connected to $connectionName")

        val errors = mutableListOf<TransferError>()

        withContext(Dispatchers.IO) {
            val indicator = EmptyProgressIndicator()
            for (file in localFiles) {
                indicator.checkCanceled()
                val localPath = Paths.get(file.path)
                val remotePath = RemotePathUtils.join(remoteTargetDir, file.name)
                uploadRecursive(sftpClient, localPath, remotePath, errors)
            }
            // Invalidate cache for the target directory so next listing is fresh
            directoryCache?.invalidate(connectionName, remoteTargetDir)
        }

        if (errors.isNotEmpty()) {
            throw BatchTransferException("Upload completed with ${errors.size} error(s)", errors)
        }
    }

    /**
     * Downloads remote entries (and directories recursively) to [localTargetDir].
     *
     * Per-file errors are collected and thrown as [BatchTransferException] at the end.
     *
     * @throws BatchTransferException if one or more files failed to download
     * @throws IllegalStateException if not connected to [connectionName]
     */
    suspend fun downloadAsync(
        remoteEntries: List<SftpEntry>,
        localTargetDir: VirtualFile,
        connectionName: String,
        fileOps: SftpFileOperations,
    ) {
        val errors = mutableListOf<TransferError>()
        val localBase = Paths.get(localTargetDir.path)

        withContext(Dispatchers.IO) {
            val indicator = EmptyProgressIndicator()
            for (entry in remoteEntries) {
                indicator.checkCanceled()
                val localPath = localBase.resolve(entry.name)
                downloadRecursive(fileOps, entry, localPath, errors)
            }
        }

        if (errors.isNotEmpty()) {
            throw BatchTransferException("Download completed with ${errors.size} error(s)", errors)
        }
    }

    // ── Recursive upload ──────────────────────────────────────────────────────

    private fun uploadRecursive(
        sftpClient: SftpClient,
        localPath: Path,
        remotePath: String,
        errors: MutableList<TransferError>,
    ) {
        if (Files.isDirectory(localPath)) {
            // Create remote directory, then recurse into children
            try {
                ensureRemoteDir(sftpClient, remotePath)
            } catch (e: Exception) {
                errors += TransferError(localPath.toString(), remotePath, e)
                return
            }
            try {
                Files.list(localPath).use { children ->
                    for (child in children) {
                        val childRemote = RemotePathUtils.join(remotePath, child.fileName.toString())
                        uploadRecursive(sftpClient, child, childRemote, errors)
                    }
                }
            } catch (e: IOException) {
                errors += TransferError(localPath.toString(), remotePath, e)
            }
        } else {
            uploadFile(sftpClient, localPath, remotePath, errors)
        }
    }

    private fun uploadFile(
        sftpClient: SftpClient,
        localPath: Path,
        remotePath: String,
        errors: MutableList<TransferError>,
    ) {
        try {
            val sizeBytes = Files.size(localPath)
            sftpClient.write(
                remotePath,
                SftpClient.OpenMode.Write,
                SftpClient.OpenMode.Create,
                SftpClient.OpenMode.Truncate,
            ).use { output ->
                Files.newInputStream(localPath).use { input ->
                    input.copyTo(output)
                }
            }
            RemoteAuditLogger.logUpload(remotePath, sizeBytes, verified = true)
        } catch (e: Exception) {
            RemoteAuditLogger.logUpload(remotePath, 0L, verified = false)
            errors += TransferError(localPath.toString(), remotePath, e)
        }
    }

    private fun ensureRemoteDir(sftpClient: SftpClient, remotePath: String) {
        try {
            sftpClient.mkdir(remotePath)
        } catch (e: Exception) {
            // Directory may already exist — check via stat
            runCatching {
                val attrs = sftpClient.stat(remotePath)
                if (!attrs.isDirectory) throw IllegalStateException("$remotePath exists but is not a directory")
            }.onFailure { throw e } // re-throw original if stat also fails
        }
    }

    // ── Recursive download ────────────────────────────────────────────────────

    private fun downloadRecursive(
        fileOps: SftpFileOperations,
        entry: SftpEntry,
        localPath: Path,
        errors: MutableList<TransferError>,
    ) {
        if (entry.isDirectory) {
            try {
                Files.createDirectories(localPath)
            } catch (e: Exception) {
                errors += TransferError(entry.path, localPath.toString(), e)
                return
            }
            try {
                val children = fileOps.listDirectory(entry.path, includeHidden = true)
                for (child in children) {
                    val childLocal = localPath.resolve(child.name)
                    downloadRecursive(fileOps, child, childLocal, errors)
                }
            } catch (e: Exception) {
                errors += TransferError(entry.path, localPath.toString(), e)
            }
        } else {
            downloadFile(fileOps, entry, localPath, errors)
        }
    }

    private fun downloadFile(
        fileOps: SftpFileOperations,
        entry: SftpEntry,
        localPath: Path,
        errors: MutableList<TransferError>,
    ) {
        try {
            fileOps.download(entry.path, localPath)
            RemoteAuditLogger.logDownload(entry.path, entry.size, success = true)
        } catch (e: Exception) {
            RemoteAuditLogger.logDownload(entry.path, entry.size, success = false)
            errors += TransferError(entry.path, localPath.toString(), e)
        }
    }

    // ── Error types ───────────────────────────────────────────────────────────

    data class TransferError(
        val sourcePath: String,
        val destinationPath: String,
        val cause: Exception,
    )

    class BatchTransferException(
        message: String,
        val errors: List<TransferError>,
    ) : Exception(message)
}
