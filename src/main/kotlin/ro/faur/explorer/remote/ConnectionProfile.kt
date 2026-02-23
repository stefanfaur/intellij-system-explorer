package ro.faur.explorer.remote

/**
 * Represents a saved SSH connection profile.
 * Stored in `.idea/sftpConnections.xml` via [RemoteConnectionSettings].
 * Passwords are NEVER stored here — they go through PasswordSafe separately.
 */
data class ConnectionProfile(
    var name: String = "",
    var host: String = "",
    var port: Int = 22,
    var username: String = "",
    var authMethod: AuthMethod = AuthMethod.AGENT,
    var keyFilePath: String? = null,
    var proxyJump: String? = null,
    var defaultRemoteDirectory: String? = null,
) {
    /** Authentication method for the connection. */
    enum class AuthMethod {
        /** Use the system SSH agent (recommended). */
        AGENT,
        /** Use a private key file. */
        KEY_FILE,
        /** Use password authentication. */
        PASSWORD,
    }

    // No password field — passwords stored via PasswordSafe or prompted per-session.
    // This is intentional: ConnectionProfile is serialized to XML and must not contain secrets.
    @Transient
    var password: String? = null
        private set
}
