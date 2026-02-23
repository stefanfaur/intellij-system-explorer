package ro.faur.explorer.remote.security

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Handles password storage via IntelliJ's PasswordSafe API.
 * Passwords are stored in the OS keychain (macOS Keychain, Linux KWallet/GNOME Keyring,
 * Windows Credential Manager).
 */
object CredentialHandler {

    private fun credentialAttributes(connectionName: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("SystemExplorer.SFTP", connectionName)
        )
    }

    /**
     * Store a password for a connection in PasswordSafe.
     */
    fun storePassword(connectionName: String, username: String, password: String) {
        val attributes = credentialAttributes(connectionName)
        val credentials = Credentials(username, password)
        PasswordSafe.instance.set(attributes, credentials)
    }

    /**
     * Retrieve a stored password for a connection.
     * Returns null if no password is stored.
     */
    fun getPassword(connectionName: String): String? {
        val attributes = credentialAttributes(connectionName)
        return PasswordSafe.instance.getPassword(attributes)
    }

    /**
     * Remove a stored password for a connection.
     */
    fun removePassword(connectionName: String) {
        val attributes = credentialAttributes(connectionName)
        PasswordSafe.instance.set(attributes, null)
    }

    /**
     * Check if a password is stored for a connection.
     */
    fun hasPassword(connectionName: String): Boolean {
        return getPassword(connectionName) != null
    }

    // ── Key passphrase (stored under a separate service name) ─────────────────

    private fun keyPassphraseAttributes(connectionName: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("SystemExplorer.SFTP.KeyPassphrase", connectionName)
        )
    }

    fun storeKeyPassphrase(connectionName: String, passphrase: String) {
        val attributes = keyPassphraseAttributes(connectionName)
        PasswordSafe.instance.set(attributes, Credentials(connectionName, passphrase))
    }

    fun getKeyPassphrase(connectionName: String): String? {
        val attributes = keyPassphraseAttributes(connectionName)
        return PasswordSafe.instance.getPassword(attributes)
    }

    fun removeKeyPassphrase(connectionName: String) {
        val attributes = keyPassphraseAttributes(connectionName)
        PasswordSafe.instance.set(attributes, null)
    }
}
