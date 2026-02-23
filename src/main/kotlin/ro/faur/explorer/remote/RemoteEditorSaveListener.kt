package ro.faur.explorer.remote

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile
import ro.faur.explorer.remote.security.RemoteAuditLogger
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Listens for document saves and automatically uploads the file back to the remote host
 * when the saved file is a tracked remote temp file (opened via [RemoteEditorManager]).
 *
 * Detection works by checking for [RemoteEditorManager.REMOTE_PATH_KEY] and
 * [RemoteEditorManager.REMOTE_CONNECTION_KEY] user data on the [VirtualFile].
 *
 * The corresponding [SftpConnectionManager] is located via the static registry in
 * [RemoteEditorManager.connectionManagerRegistry].
 *
 * Registration: declare this class as a `fileDocumentManagerListener` extension in plugin.xml:
 * ```xml
 * <applicationListeners>
 *   <listener class="ro.faur.explorer.remote.RemoteEditorSaveListener"
 *             topic="com.intellij.openapi.fileEditor.FileDocumentManagerListener"/>
 * </applicationListeners>
 * ```
 */
class RemoteEditorSaveListener : FileDocumentManagerListener {

    private val log = Logger.getInstance(RemoteEditorSaveListener::class.java)

    companion object {
        private const val NOTIFICATION_GROUP = "Remote Explorer"
    }

    /**
     * Called before IntelliJ writes the document to disk.
     * We intercept saves of remote temp files here and schedule an upload.
     *
     * Note: the actual disk write by IntelliJ happens *after* this callback returns,
     * so we queue the upload on a background thread that waits briefly before reading
     * the saved file.
     */
    override fun beforeDocumentSaving(document: Document) {
        val fileDocManager = FileDocumentManager.getInstance()
        val virtualFile: VirtualFile = fileDocManager.getFile(document) ?: return

        val remotePath = virtualFile.getUserData(RemoteEditorManager.REMOTE_PATH_KEY) ?: return
        val connectionName = virtualFile.getUserData(RemoteEditorManager.REMOTE_CONNECTION_KEY) ?: return

        val connectionManager = RemoteEditorManager.connectionManagerRegistry[connectionName]
        if (connectionManager == null) {
            log.warn("RemoteEditorSaveListener: no SftpConnectionManager registered for '$connectionName'")
            return
        }

        if (!connectionManager.isConnected(connectionName)) {
            showErrorNotification(
                "Upload skipped — not connected to '$connectionName'",
                "Remote Explorer Save"
            )
            return
        }

        // Schedule upload after the file is written to disk
        Thread {
            // Small delay to allow IntelliJ to finish writing the document to disk
            Thread.sleep(200)
            performUpload(virtualFile, remotePath, connectionName, connectionManager)
        }.also {
            it.isDaemon = true
            it.name = "RemoteEditorUpload[$remotePath]"
        }.start()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun performUpload(
        virtualFile: VirtualFile,
        remotePath: String,
        connectionName: String,
        connectionManager: SftpConnectionManager,
    ) {
        val sftpClient = connectionManager.getSftpClient(connectionName)
        if (sftpClient == null) {
            log.error("RemoteEditorSaveListener: SFTP client unavailable for '$connectionName'")
            showErrorNotification(
                "Upload failed for $remotePath — SFTP client unavailable",
                "Remote Save Failed"
            )
            return
        }

        val localPath = runCatching { Paths.get(virtualFile.path) }.getOrNull()
        if (localPath == null || !Files.exists(localPath)) {
            showErrorNotification(
                "Upload failed for $remotePath — local temp file not found",
                "Remote Save Failed"
            )
            return
        }

        try {
            val sizeBytes = Files.size(localPath)

            sftpClient.write(
                remotePath,
                org.apache.sshd.sftp.client.SftpClient.OpenMode.Write,
                org.apache.sshd.sftp.client.SftpClient.OpenMode.Create,
                org.apache.sshd.sftp.client.SftpClient.OpenMode.Truncate,
            ).use { output ->
                Files.newInputStream(localPath).use { input ->
                    input.copyTo(output)
                }
            }

            // Verify upload integrity: compare local size with remote stat
            var verified = true
            try {
                val remoteAttrs = sftpClient.stat(remotePath)
                if (remoteAttrs.size != sizeBytes) {
                    verified = false
                    showErrorNotification(
                        "Upload size mismatch for $remotePath: local=$sizeBytes, remote=${remoteAttrs.size}",
                        "Remote Save Warning"
                    )
                }
            } catch (statEx: Exception) {
                log.warn("Could not verify upload for $remotePath: ${statEx.message}")
            }

            RemoteAuditLogger.logUpload(remotePath, sizeBytes, verified = verified)

            showSuccessNotification(
                "Saved to remote: $remotePath",
                connectionName
            )
            log.info("RemoteEditorSaveListener: uploaded $localPath → $remotePath on $connectionName")
        } catch (e: Exception) {
            RemoteAuditLogger.logUpload(remotePath, 0L, verified = false)
            log.error("RemoteEditorSaveListener: upload failed for $remotePath on $connectionName", e)
            showErrorNotification(
                "Upload failed for $remotePath: ${e.message}",
                "Remote Save Failed"
            )
        }
    }

    private fun showSuccessNotification(content: String, title: String) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content, NotificationType.INFORMATION)
                .notify(null)
        }.onFailure { log.warn("Could not show success notification: ${it.message}") }
    }

    private fun showErrorNotification(content: String, title: String) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content, NotificationType.ERROR)
                .notify(null)
        }.onFailure { log.warn("Could not show error notification: ${it.message}") }
    }
}
