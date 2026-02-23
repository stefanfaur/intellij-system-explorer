package ro.faur.explorer.remote

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.session.helpers.AbstractSession
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import java.security.KeyPair
import java.nio.file.Paths
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.util.security.SecurityUtils

/**
 * Manages SSH/SFTP connections keyed by [ConnectionProfile.name].
 *
 * Each connection is represented by a [ConnectionEntry] which holds a
 * [ClientSession] and a lazily-created [SftpClient].
 *
 * Thread-safe: all mutable state lives in a [ConcurrentHashMap].
 *
 * @param connectTimeoutMs Timeout (milliseconds) used for both TCP connect and
 *   SSH authentication. Defaults to 10 000 ms.
 */
class SftpConnectionManager(
    private val connectTimeoutMs: Long = 10_000L,
) {
    // ── Singleton SshClient ──────────────────────────────────────────────────

    private val lazyClient = lazy {
        SshClient.setUpDefaultClient().also { client ->
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(60))
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, 3)
            client.start()
        }
    }
    private val sshClient: SshClient by lazyClient

    // ── Internal state ───────────────────────────────────────────────────────

    private data class ConnectionEntry(
        val session: ClientSession,
        var sftpClient: SftpClient? = null,
    )

    private val connections = ConcurrentHashMap<String, ConnectionEntry>()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Opens a new SSH session for [profile] and authenticates using the
     * supplied [password] or key-pair, depending on [profile.authMethod].
     *
     * Stores the session internally under [ConnectionProfile.name] so it can
     * be retrieved later via [getSession] / [getSftpClient].
     *
     * @return The authenticated [ClientSession].
     * @throws Exception if the connection or authentication fails.
     */
    fun connect(
        profile: ConnectionProfile,
        password: String? = null,
        keyPassphrase: String? = null,
    ): ClientSession {
        val session = sshClient
            .connect(profile.username, profile.host, profile.port)
            .verify(connectTimeoutMs)
            .session

        when (profile.authMethod) {
            ConnectionProfile.AuthMethod.PASSWORD -> {
                requireNotNull(password) { "Password required for PASSWORD auth method" }
                session.addPasswordIdentity(password)
            }

            ConnectionProfile.AuthMethod.KEY_FILE -> {
                val keyPair = loadKeyPair(profile.keyFilePath!!, keyPassphrase)
                session.addPublicKeyIdentity(keyPair)
            }

            ConnectionProfile.AuthMethod.AGENT -> {
                // No sshd-agent on classpath — load default key files the same
                // way SftpFileOperations.createWithAgent() does.
                val home = System.getProperty("user.home")
                listOf("id_ed25519", "id_ecdsa", "id_rsa", "id_dsa")
                    .map { java.io.File("$home/.ssh/$it") }
                    .filter { it.exists() }
                    .forEach { keyFile ->
                        runCatching {
                            Files.newInputStream(keyFile.toPath()).use { inputStream ->
                                val pairs = SecurityUtils.loadKeyPairIdentities(
                                    null, null, inputStream, FilePasswordProvider.EMPTY
                                )
                                pairs.forEach { session.addPublicKeyIdentity(it) }
                            }
                        }
                    }
            }
        }

        session.auth().verify(connectTimeoutMs)

        connections[profile.name] = ConnectionEntry(session)
        return session
    }

    /**
     * Closes the SFTP client (if any) and the SSH session for [connectionName],
     * then removes the entry from the internal map.
     *
     * A no-op if no connection with that name exists.
     */
    fun disconnect(connectionName: String) {
        val entry = connections.remove(connectionName) ?: return
        entry.sftpClient?.close()
        entry.session.close()
    }

    /**
     * Returns `true` if a session for [connectionName] exists in the internal
     * map and its underlying channel is still open.
     */
    fun isConnected(connectionName: String): Boolean {
        val entry = connections[connectionName] ?: return false
        return entry.session.isOpen
    }

    /**
     * Returns a ready-to-use [SftpClient] for [connectionName].
     *
     * The client is created lazily on the first call and cached for subsequent
     * calls. Returns `null` if no connection with that name exists.
     */
    fun getSftpClient(connectionName: String): SftpClient? {
        val entry = connections[connectionName] ?: return null
        if (entry.sftpClient == null) {
            entry.sftpClient = SftpClientFactory.instance().createSftpClient(entry.session)
        }
        return entry.sftpClient
    }

    /**
     * Returns the raw [ClientSession] for [connectionName], or `null` if no
     * such connection exists or the session has already been removed.
     *
     * Useful for opening exec channels (e.g. `tail -f`, git commands).
     */
    fun getSession(connectionName: String): ClientSession? =
        connections[connectionName]?.session

    /**
     * Disconnects all active connections and stops the shared [SshClient].
     *
     * After calling this method, this manager is no longer usable.
     */
    fun shutdown() {
        for (name in connections.keys.toList()) {
            disconnect(name)
        }
        if (lazyClient.isInitialized()) {
            sshClient.stop()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Loads a [KeyPair] from the given PEM/OpenSSH private-key file path.
     * Passphrase-protected keys are not supported here (no passphrase provider).
     */
    private fun loadKeyPair(keyFilePath: String, keyPassphrase: String? = null): KeyPair {
        val keyPath = Paths.get(keyFilePath)
        val provider = if (keyPassphrase != null)
            FilePasswordProvider { _, _, _ -> keyPassphrase }
        else
            FilePasswordProvider.EMPTY
        Files.newInputStream(keyPath).use { inputStream ->
            val pairs = SecurityUtils.loadKeyPairIdentities(null, null, inputStream, provider)
            return pairs.first()
        }
    }
}
