package ro.faur.explorer.remote

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.CharsetToolkit
import ro.faur.explorer.remote.security.RemoteAuditLogger
import ro.faur.explorer.remote.security.SecureTempFileManager
import java.nio.file.Files

/**
 * Manages the edit-via-temp-file workflow for remote files:
 * 1. Optionally check file size and warn/block
 * 2. Download to a secure temp directory
 * 3. Detect encoding
 * 4. Tag the [com.intellij.openapi.vfs.VirtualFile] with remote metadata and the live
 *    [SftpFileOperations] handle so [RemoteEditorSaveListener] can upload on save
 * 5. Pre-authorize the file to skip IntelliJ's "non-project files" protection prompt
 * 6. Open in the editor
 *
 * [openRemoteFile] spawns its own background thread and dispatches UI to the EDT internally,
 * so callers may invoke it from any thread.
 *
 * Keys stored on the [com.intellij.openapi.vfs.VirtualFile]:
 * - [REMOTE_PATH_KEY]       — remote path string
 * - [REMOTE_HOST_KEY]       — SSH host string
 * - [REMOTE_CONNECTION_KEY] — connection name (for notifications)
 * - [SFTP_OPS_KEY]          — live [SftpFileOperations] used by [RemoteEditorSaveListener]
 */
class RemoteEditorManager(
    private val project: Project,
    private val tempFileManager: SecureTempFileManager,
    private val fileSizeChecker: FileSizeLimitChecker = FileSizeLimitChecker(10),
) {
    private val log = Logger.getInstance(RemoteEditorManager::class.java)

    companion object {
        val REMOTE_PATH_KEY       = Key.create<String>("SystemExplorer.RemotePath")
        val REMOTE_HOST_KEY       = Key.create<String>("SystemExplorer.RemoteHost")
        val REMOTE_CONNECTION_KEY = Key.create<String>("SystemExplorer.RemoteConnection")

        /** Live [SftpFileOperations] stored on the VirtualFile for save-back by [RemoteEditorSaveListener]. */
        val SFTP_OPS_KEY = Key.create<SftpFileOperations>("SystemExplorer.SftpOps")

        /**
         * Legacy registry kept for backward compatibility.
         * New code uses [SFTP_OPS_KEY] on the VirtualFile instead.
         */
        val connectionManagerRegistry: MutableMap<String, SftpConnectionManager> =
            java.util.concurrent.ConcurrentHashMap()
    }

    fun isRemoteTempFile(file: com.intellij.openapi.vfs.VirtualFile): Boolean =
        file.getUserData(REMOTE_PATH_KEY) != null

    fun getRemotePath(file: com.intellij.openapi.vfs.VirtualFile): String? =
        file.getUserData(REMOTE_PATH_KEY)

    fun getConnectionName(file: com.intellij.openapi.vfs.VirtualFile): String? =
        file.getUserData(REMOTE_CONNECTION_KEY)

    /**
     * Downloads [remotePath] to a secure temp file and opens it in the IntelliJ editor.
     *
     * Self-contained: spawns a background thread for I/O and dispatches to the EDT
     * for any UI (dialogs, file opening). Safe to call from any thread.
     */
    fun openRemoteFile(
        connectionName: String,
        host: String,
        remotePath: String,
        fileOps: SftpFileOperations,
    ) {
        Thread {
            // ── File-size guard ──────────────────────────────────────────────────
            val remoteStat = try { fileOps.stat(remotePath) } catch (_: Exception) { null }
            if (remoteStat != null) {
                when (fileSizeChecker.check(remoteStat.size)) {
                    FileSizeLimitChecker.Action.BLOCK_TOO_LARGE -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog(
                                project,
                                "File is too large to open in the editor " +
                                        "(${remoteStat.size / (1024 * 1024)} MB). Download it to disk instead.",
                                "File Too Large",
                            )
                        }
                        return@Thread
                    }
                    FileSizeLimitChecker.Action.WARN_LARGE -> {
                        var proceed = false
                        ApplicationManager.getApplication().invokeAndWait {
                            proceed = Messages.showYesNoDialog(
                                project,
                                "File is large (${remoteStat.size / (1024 * 1024)} MB). " +
                                        "Opening may be slow. Continue?",
                                "Large File Warning",
                                Messages.getWarningIcon(),
                            ) == Messages.YES
                        }
                        if (!proceed) return@Thread
                    }
                    FileSizeLimitChecker.Action.OPEN_NORMALLY -> { /* proceed */ }
                }
            }

            // ── Download ─────────────────────────────────────────────────────────
            val tempPath = try {
                tempFileManager.createTempFile(host, remotePath)
            } catch (e: Exception) {
                log.error("Failed to create temp file for $remotePath", e)
                return@Thread
            }

            try {
                fileOps.download(remotePath, tempPath)
                RemoteAuditLogger.logDownload(remotePath, Files.size(tempPath), success = true)
            } catch (e: Exception) {
                log.error("Failed to download $remotePath", e)
                RemoteAuditLogger.logDownload(remotePath, 0L, success = false)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Failed to open $remotePath: ${e.message}", "Open Failed")
                }
                return@Thread
            }

            // ── Encoding detection ───────────────────────────────────────────────
            val charset = try {
                val bytes = Files.readAllBytes(tempPath)
                val toolkit = CharsetToolkit(bytes, Charsets.UTF_8, false)
                toolkit.guessFromBOM() ?: toolkit.guessEncoding(bytes.size.coerceAtMost(8192))
            } catch (e: Exception) {
                log.debug("Could not detect encoding for $remotePath: ${e.message}")
                Charsets.UTF_8
            }

            // ── Open in editor (EDT) ─────────────────────────────────────────────
            ApplicationManager.getApplication().invokeLater {
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempPath)
                    ?: return@invokeLater
                virtualFile.charset = charset
                virtualFile.putUserData(REMOTE_PATH_KEY,       remotePath)
                virtualFile.putUserData(REMOTE_HOST_KEY,       host)
                virtualFile.putUserData(REMOTE_CONNECTION_KEY, connectionName)
                virtualFile.putUserData(SFTP_OPS_KEY,          fileOps)
                NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }.also { it.isDaemon = true; it.name = "RemoteOpen[$remotePath]" }.start()
    }
}
