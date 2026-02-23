package ro.faur.explorer.light.remote

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.security.RemoteAuditLogger

class RemoteAuditLoggerTest : BasePlatformTestCase() {

    fun `test logConnect formats correctly`() {
        val message = RemoteAuditLogger.formatConnect("deploy", "prod.example.com", 22, success = true)
        assertEquals("AUDIT: SSH CONNECT deploy@prod.example.com:22 — SUCCESS", message)
    }

    fun `test logConnect failure formats correctly`() {
        val message = RemoteAuditLogger.formatConnect("admin", "prod.example.com", 22, success = false)
        assertEquals("AUDIT: SSH CONNECT admin@prod.example.com:22 — FAILED", message)
    }

    fun `test logDownload formats with size`() {
        val message = RemoteAuditLogger.formatDownload("/var/www/app.yml", 23552L, success = true)
        assertEquals("AUDIT: SFTP DOWNLOAD /var/www/app.yml — SUCCESS (23KB)", message)
    }

    fun `test logUpload formats with size and verification`() {
        val message = RemoteAuditLogger.formatUpload("/var/www/app.yml", 24576L, verified = true)
        assertEquals("AUDIT: SFTP UPLOAD /var/www/app.yml — SUCCESS (24KB, verified)", message)
    }

    fun `test logAuthFailure formats with reason`() {
        val message = RemoteAuditLogger.formatAuthFailure("admin", "prod.example.com", 22, "invalid password")
        assertEquals("AUDIT: SSH AUTH FAILED admin@prod.example.com:22 — invalid password", message)
    }
}
