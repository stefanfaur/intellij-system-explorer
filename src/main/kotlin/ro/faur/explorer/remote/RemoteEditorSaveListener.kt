package ro.faur.explorer.remote

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import ro.faur.explorer.remote.security.RemoteAuditLogger
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Listens for document saves and automatically uploads the file back to the remote host
 * when the saved file is a tracked remote temp file (opened via [RemoteEditorManager]).
 *
 * Detection works by checking for [RemoteEditorManager.REMOTE_PATH_KEY] and
 * [RemoteEditorManager.REMOTE_CONNECTION_KEY] user data on the [VirtualFile].
 *
 * The live [SftpFileOperations] handle is read from [RemoteEditorManager.SFTP_OPS_KEY]
 * on the [VirtualFile].
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
    // Shared application executor — platform-managed, no explicit shutdown needed
    private val uploadExecutor = AppExecutorUtil.getAppScheduledExecutorService()
    private val pendingUploads = ConcurrentHashMap<String, ScheduledFuture<*>>()

    companion object {
        private const val NOTIFICATION_GROUP = "Remote Explorer"
    }

    /**
     * Called before IntelliJ writes the document to disk.
     * We debounce rapid successive saves (100 ms window) per remote path; the scheduled
     * upload runs after the debounce delay, by which time IntelliJ has completed the
     * actual disk write.
     */
    override fun beforeDocumentSaving(document: Document) {
        val virtualFile: VirtualFile = FileDocumentManager.getInstance().getFile(document) ?: return

        val remotePath = virtualFile.getUserData(RemoteEditorManager.REMOTE_PATH_KEY) ?: return
        val connectionName = virtualFile.getUserData(RemoteEditorManager.REMOTE_CONNECTION_KEY) ?: return

        // Cancel any previously scheduled upload for this file
        pendingUploads[remotePath]?.cancel(false)
        pendingUploads[remotePath] = uploadExecutor.schedule({
            pendingUploads.remove(remotePath)
            performUpload(virtualFile, remotePath, connectionName)
        }, 100, TimeUnit.MILLISECONDS)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun performUpload(
        virtualFile: VirtualFile,
        remotePath: String,
        connectionName: String,
    ) {
        val localPath = runCatching { Paths.get(virtualFile.path) }.getOrNull()
        if (localPath == null || !Files.exists(localPath)) {
            showErrorNotification("Upload failed for $remotePath — local temp file not found", "Remote Save Failed")
            return
        }

        val fileOps = virtualFile.getUserData(RemoteEditorManager.SFTP_OPS_KEY)
            ?: run { log.warn("No SFTP ops for $remotePath, cannot upload"); return }
        try {
            val sizeBytes = Files.size(localPath)
            fileOps.upload(localPath, remotePath)
            RemoteAuditLogger.logUpload(remotePath, sizeBytes, verified = true)
            showSuccessNotification("Saved to remote: $remotePath", connectionName)
            log.info("RemoteEditorSaveListener: uploaded $localPath → $remotePath on $connectionName")
        } catch (e: Exception) {
            RemoteAuditLogger.logUpload(remotePath, 0L, verified = false)
            log.error("RemoteEditorSaveListener: upload failed for $remotePath on $connectionName", e)
            showErrorNotification("Upload failed for $remotePath: ${e.message}", "Remote Save Failed")
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
