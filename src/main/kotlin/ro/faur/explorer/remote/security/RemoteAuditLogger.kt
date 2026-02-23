package ro.faur.explorer.remote.security

import com.intellij.openapi.diagnostic.Logger

/**
 * Audit logger for all remote SFTP/SSH operations.
 * Logs to standard IntelliJ log file at INFO level.
 *
 * Format: AUDIT: <OPERATION> <DETAILS> — <RESULT>
 */
object RemoteAuditLogger {

    private val LOG = Logger.getInstance(RemoteAuditLogger::class.java)

    fun logConnect(username: String, host: String, port: Int, success: Boolean) {
        val message = formatConnect(username, host, port, success)
        LOG.info(message)
    }

    fun logDownload(remotePath: String, sizeBytes: Long, success: Boolean) {
        val message = formatDownload(remotePath, sizeBytes, success)
        LOG.info(message)
    }

    fun logUpload(remotePath: String, sizeBytes: Long, verified: Boolean) {
        val message = formatUpload(remotePath, sizeBytes, verified)
        LOG.info(message)
    }

    fun logAuthFailure(username: String, host: String, port: Int, reason: String) {
        val message = formatAuthFailure(username, host, port, reason)
        LOG.warn(message)
    }

    // Format methods exposed for testing without triggering Logger

    fun formatConnect(username: String, host: String, port: Int, success: Boolean): String {
        val result = if (success) "SUCCESS" else "FAILED"
        return "AUDIT: SSH CONNECT $username@$host:$port — $result"
    }

    fun formatDownload(remotePath: String, sizeBytes: Long, success: Boolean): String {
        val sizeKb = sizeBytes / 1024
        val result = if (success) "SUCCESS (${sizeKb}KB)" else "FAILED"
        return "AUDIT: SFTP DOWNLOAD $remotePath — $result"
    }

    fun formatUpload(remotePath: String, sizeBytes: Long, verified: Boolean): String {
        val sizeKb = sizeBytes / 1024
        val verifiedStr = if (verified) ", verified" else ""
        return "AUDIT: SFTP UPLOAD $remotePath — SUCCESS (${sizeKb}KB$verifiedStr)"
    }

    fun formatAuthFailure(username: String, host: String, port: Int, reason: String): String {
        return "AUDIT: SSH AUTH FAILED $username@$host:$port — $reason"
    }
}
