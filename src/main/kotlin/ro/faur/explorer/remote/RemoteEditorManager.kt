package ro.faur.explorer.remote

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.CharsetToolkit
import ro.faur.explorer.remote.security.RemoteAuditLogger
import ro.faur.explorer.remote.security.SecureTempFileManager
import java.nio.file.Files

/**
 * Manages the edit-via-temp-file workflow:
 * 1. Download remote file to secure temp directory
 * 2. Open in IntelliJ editor
 * 3. Upload back to remote on save (handled by [RemoteEditorSaveListener])
 *
 * Keys [REMOTE_PATH_KEY] and [REMOTE_CONNECTION_KEY] are attached to the [VirtualFile]
 * so that the save listener can locate the connection and upload the file on save.
 */
class RemoteEditorManager(
    private val project: Project,
    private val tempFileManager: SecureTempFileManager,
    private val fileSizeChecker: FileSizeLimitChecker = FileSizeLimitChecker(10),
) {
    private val log = Logger.getInstance(RemoteEditorManager::class.java)

    companion object {
        val REMOTE_PATH_KEY = Key.create<String>("SystemExplorer.RemotePath")
        val REMOTE_HOST_KEY = Key.create<String>("SystemExplorer.RemoteHost")
        val REMOTE_CONNECTION_KEY = Key.create<String>("SystemExplorer.RemoteConnection")

        /**
         * Global registry that maps connectionName -> SftpConnectionManager.
         * Used by [RemoteEditorSaveListener] to find the manager for a connection.
         *
         * Populated by whatever code creates [RemoteEditorManager] instances (e.g. the tool window factory).
         */
        val connectionManagerRegistry: MutableMap<String, SftpConnectionManager> =
            java.util.concurrent.ConcurrentHashMap()
    }

    /**
     * Download a remote file, open it in the editor, and tag the VirtualFile
     * with remote metadata so [RemoteEditorSaveListener] can upload on save.
     */
    fun openRemoteFile(
        connectionName: String,
        host: String,
        remotePath: String,
        fileOps: SftpFileOperations
    ) {
        // Check file size before downloading
        val remoteStat = try { fileOps.stat(remotePath) } catch (_: Exception) { null }
        if (remoteStat != null) {
            when (fileSizeChecker.check(remoteStat.size)) {
                FileSizeLimitChecker.Action.BLOCK_TOO_LARGE -> {
                    Messages.showWarningDialog(
                        project,
                        "File is too large to open in the editor (${remoteStat.size / (1024 * 1024)} MB). Download it to disk instead.",
                        "File Too Large"
                    )
                    return
                }
                FileSizeLimitChecker.Action.WARN_LARGE -> {
                    val proceed = Messages.showYesNoDialog(
                        project,
                        "File is large (${remoteStat.size / (1024 * 1024)} MB). Opening may be slow. Continue?",
                        "Large File Warning",
                        Messages.getWarningIcon()
                    )
                    if (proceed != Messages.YES) return
                }
                FileSizeLimitChecker.Action.OPEN_NORMALLY -> { /* proceed */ }
            }
        }

        val tempPath = tempFileManager.createTempFile(host, remotePath)

        // Download to temp file
        fileOps.download(remotePath, tempPath)
        val fileSize = Files.size(tempPath)
        RemoteAuditLogger.logDownload(remotePath, fileSize, success = true)

        // Refresh and open in editor
        val virtualFile: VirtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(tempPath)
            ?: return

        // Detect file encoding and set on VirtualFile
        try {
            val bytes = Files.readAllBytes(tempPath)
            val toolkit = CharsetToolkit(bytes, Charsets.UTF_8, false)
            val charset = toolkit.guessFromBOM() ?: toolkit.guessEncoding(bytes.size.coerceAtMost(8192))
            virtualFile.charset = charset
        } catch (e: Exception) {
            log.debug("Could not detect encoding for $remotePath: ${e.message}")
        }

        // Tag with remote metadata — picked up by RemoteEditorSaveListener on save
        virtualFile.putUserData(REMOTE_PATH_KEY, remotePath)
        virtualFile.putUserData(REMOTE_HOST_KEY, host)
        virtualFile.putUserData(REMOTE_CONNECTION_KEY, connectionName)

        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    /**
     * Returns true if [file] is a tracked remote temp file (has remote metadata attached).
     */
    fun isRemoteTempFile(file: VirtualFile): Boolean =
        file.getUserData(REMOTE_PATH_KEY) != null

    /**
     * Returns the remote path stored on the VirtualFile, or null.
     */
    fun getRemotePath(file: VirtualFile): String? =
        file.getUserData(REMOTE_PATH_KEY)

    /**
     * Returns the connection name stored on the VirtualFile, or null.
     */
    fun getConnectionName(file: VirtualFile): String? =
        file.getUserData(REMOTE_CONNECTION_KEY)
}
