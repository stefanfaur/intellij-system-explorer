package ro.faur.explorer.remote

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import org.apache.sshd.sftp.common.SftpConstants
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Wraps an SFTP client session and exposes file-system operations against a remote server.
 *
 * Obtain an instance via [SftpFileOperations.create].
 */
class SftpFileOperations private constructor(
    private val sshClient: SshClient,
    private val session: ClientSession,
    private val sftp: SftpClient,
) : Closeable {

    // ── Directory listing ─────────────────────────────────────────────────────

    /**
     * Lists the entries of [path] on the remote server.
     *
     * The synthetic `.` and `..` entries are always excluded. When [includeHidden]
     * is `false` (the default), entries whose names start with `.` are also excluded.
     */
    fun listDirectory(path: String, includeHidden: Boolean = true): List<SftpEntry> {
        val entries = mutableListOf<SftpEntry>()
        for (entry in sftp.readDir(path)) {
            val name = entry.filename
            if (name == "." || name == "..") continue
            if (!includeHidden && name.startsWith(".")) continue
            entries += entry.toSftpEntry(path)
        }
        return entries
    }

    // ── Transfer ──────────────────────────────────────────────────────────────

    /** Downloads the remote file at [remotePath] and writes it to [localPath]. */
    fun download(remotePath: String, localPath: Path) {
        sftp.read(remotePath).use { remoteIn ->
            Files.newOutputStream(localPath).use { localOut ->
                remoteIn.copyTo(localOut)
            }
        }
    }

    /** Uploads [localPath] to [remotePath] on the remote server, overwriting if it exists. */
    fun upload(localPath: Path, remotePath: String) {
        Files.newInputStream(localPath).use { localIn ->
            sftp.write(
                remotePath,
                SftpClient.OpenMode.Write,
                SftpClient.OpenMode.Create,
                SftpClient.OpenMode.Truncate,
            ).use { remoteOut ->
                localIn.copyTo(remoteOut)
            }
        }
    }

    // ── Directory management ──────────────────────────────────────────────────

    /**
     * Creates a new directory at [path].
     *
     * Throws if the path already exists or the parent is missing.
     */
    fun mkdir(path: String) {
        sftp.mkdir(path)
    }

    // ── Rename / delete ───────────────────────────────────────────────────────

    /** Renames (moves) a remote entry from [oldPath] to [newPath]. */
    fun rename(oldPath: String, newPath: String) {
        sftp.rename(oldPath, newPath)
    }

    /** Removes a regular file (or symlink) at [path]. */
    fun deleteFile(path: String) {
        sftp.remove(path)
    }

    /** Removes an *empty* directory at [path]. */
    fun deleteDirectory(path: String) {
        sftp.rmdir(path)
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Returns the canonical absolute path of the user's home directory on the remote server.
     * Uses SFTP's `realpath(".")` which the server resolves relative to the connection's CWD
     * (almost always the home directory). Falls back to `"/"` on any error.
     */
    fun homeDir(): String = runCatching { sftp.canonicalPath(".") }.getOrDefault("/")

    /**
     * Returns the attributes of the entry at [path].
     * Throws if the path does not exist.
     */
    fun stat(path: String): SftpEntry {
        val attrs = sftp.stat(path)
        val name = path.substringAfterLast('/')
        val isDirectory = attrs.isDirectory
        val isSymlink = attrs.isSymbolicLink
        val size = attrs.size
        val lastModified = attrs.modifyTime?.toInstant()?.toEpochMilli() ?: 0L
        val permissions = attrs.permissions?.let { permsToString(it) }
        return SftpEntry(
            name = name,
            isDirectory = isDirectory,
            size = size,
            isSymlink = isSymlink,
            isBrokenSymlink = false,
            permissions = permissions,
            lastModified = lastModified,
            path = path,
        )
    }

    /**
     * Resolves the target of a symlink at [path].
     * Returns `null` if the path is not a symlink or if resolution fails.
     */
    fun resolveLink(path: String): String? = runCatching { sftp.readLink(path) }.getOrNull()

    // ── Closeable ─────────────────────────────────────────────────────────────

    override fun close() {
        runCatching { sftp.close() }
        runCatching { session.close(false) }
        runCatching { sshClient.stop() }
    }

    // ── Companion / factory ───────────────────────────────────────────────────

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val AUTH_TIMEOUT_MS = 8_000L

        /**
         * Opens an SSH connection to [host]:[port], authenticates with [username]/[password],
         * starts an SFTP channel, and returns a ready-to-use [SftpFileOperations].
         *
         * The caller is responsible for calling [close] when finished.
         */
        fun create(
            host: String,
            port: Int,
            username: String,
            password: String,
        ): SftpFileOperations {
            val client = SshClient.setUpDefaultClient().also { it.start() }
            try {
                val session = client.connect(username, host, port)
                    .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .session
                session.addPasswordIdentity(password)
                session.auth().verify(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val sftp = SftpClientFactory.instance().createSftpClient(session)
                return SftpFileOperations(client, session, sftp)
            } catch (e: Exception) {
                client.stop()
                throw e
            }
        }

        /**
         * Connects using a PEM/OpenSSH private key file.
         * [keyPassphrase] is used to decrypt the key if it is passphrase-protected;
         * pass null (or omit) for unencrypted keys.
         */
        fun createWithKeyFile(
            host: String,
            port: Int,
            username: String,
            keyFilePath: String,
            keyPassphrase: String? = null,
        ): SftpFileOperations {
            val client = SshClient.setUpDefaultClient().also { it.start() }
            try {
                val session = client.connect(username, host, port)
                    .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .session
                val keyPath = java.nio.file.Paths.get(keyFilePath)
                val passwordProvider = if (keyPassphrase != null)
                    org.apache.sshd.common.config.keys.FilePasswordProvider { _, _, _ -> keyPassphrase }
                else
                    org.apache.sshd.common.config.keys.FilePasswordProvider.EMPTY
                java.nio.file.Files.newInputStream(keyPath).use { inputStream ->
                    val pairs = org.apache.sshd.common.util.security.SecurityUtils
                        .loadKeyPairIdentities(null, null, inputStream, passwordProvider)
                    session.addPublicKeyIdentity(pairs.first())
                }
                session.auth().verify(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val sftp = SftpClientFactory.instance().createSftpClient(session)
                return SftpFileOperations(client, session, sftp)
            } catch (e: Exception) {
                client.stop()
                throw e
            }
        }

        /**
         * Connects by trying each of the user's default SSH key files in priority order
         * (~/.ssh/id_ed25519, id_ecdsa, id_rsa, id_dsa).  This mirrors what the system
         * `ssh` client does when no explicit key is given.
         *
         * Note: `sshd-agent` is not on the classpath, so `SSH_AUTH_SOCK` cannot be used
         * directly.  Loading default key files achieves the same result for the common case.
         */
        fun createWithAgent(
            host: String,
            port: Int,
            username: String,
        ): SftpFileOperations {
            val home = System.getProperty("user.home")
            val defaultKeys = listOf("id_ed25519", "id_ecdsa", "id_rsa", "id_dsa")
                .map { java.io.File("$home/.ssh/$it") }
                .filter { it.exists() }

            if (defaultKeys.isEmpty()) {
                throw IllegalStateException(
                    "No default SSH keys found in ~/.ssh/. " +
                    "Add a key or switch to 'Key file' auth."
                )
            }

            val client = SshClient.setUpDefaultClient().also { it.start() }
            try {
                val session = client.connect(username, host, port)
                    .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .session

                for (keyFile in defaultKeys) {
                    runCatching {
                        java.nio.file.Files.newInputStream(keyFile.toPath()).use { inputStream ->
                            val pairs = org.apache.sshd.common.util.security.SecurityUtils
                                .loadKeyPairIdentities(null, null, inputStream,
                                    org.apache.sshd.common.config.keys.FilePasswordProvider.EMPTY)
                            pairs.forEach { session.addPublicKeyIdentity(it) }
                        }
                    }
                    // silently skip keys that fail to load (e.g. encrypted without passphrase)
                }

                session.auth().verify(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val sftp = SftpClientFactory.instance().createSftpClient(session)
                return SftpFileOperations(client, session, sftp)
            } catch (e: Exception) {
                client.stop()
                throw e
            }
        }

        /**
         * Dispatches to the correct create() overload based on [profile.authMethod].
         * [password] is only used when authMethod == PASSWORD.
         * [keyPassphrase] is only used when authMethod == KEY_FILE.
         */
        fun create(
            profile: ConnectionProfile,
            password: String? = null,
            keyPassphrase: String? = null,
        ): SftpFileOperations {
            return when (profile.authMethod) {
                ConnectionProfile.AuthMethod.PASSWORD -> {
                    requireNotNull(password) { "Password required for PASSWORD auth" }
                    create(profile.host, profile.port, profile.username, password)
                }
                ConnectionProfile.AuthMethod.KEY_FILE -> {
                    requireNotNull(profile.keyFilePath) { "keyFilePath required for KEY_FILE auth" }
                    createWithKeyFile(profile.host, profile.port, profile.username, profile.keyFilePath!!, keyPassphrase)
                }
                ConnectionProfile.AuthMethod.AGENT ->
                    createWithAgent(profile.host, profile.port, profile.username)
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        /**
         * Converts a POSIX permission bitmask to a 9-character string such as `rwxr-xr-x`.
         */
        private fun permsToString(perms: Int): String {
            val chars = CharArray(9)
            val bits = intArrayOf(
                SftpConstants.S_IRUSR, SftpConstants.S_IWUSR, SftpConstants.S_IXUSR,
                SftpConstants.S_IRGRP, SftpConstants.S_IWGRP, SftpConstants.S_IXGRP,
                SftpConstants.S_IROTH, SftpConstants.S_IWOTH, SftpConstants.S_IXOTH,
            )
            val labels = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')
            for (i in bits.indices) {
                chars[i] = if (perms and bits[i] != 0) labels[i] else '-'
            }
            return String(chars)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun SftpClient.DirEntry.toSftpEntry(parentPath: String): SftpEntry {
        val attrs = this.attributes
        val isDir = attrs.isDirectory
        val isSym = attrs.isSymbolicLink
        val entryPath = if (parentPath == "/") "/$filename" else "$parentPath/$filename"
        val broken = if (isSym) {
            // Probe the symlink target: stat() follows symlinks, so if the target
            // doesn't exist it throws. That means the symlink is broken.
            runCatching { sftp.stat(entryPath) }.isFailure
        } else false
        return SftpEntry(
            name = filename,
            isDirectory = isDir,
            size = attrs.size,
            isSymlink = isSym,
            isBrokenSymlink = broken,
            permissions = attrs.permissions?.let { permsToString(it) },
            lastModified = attrs.modifyTime?.toInstant()?.toEpochMilli() ?: 0L,
            path = entryPath,
        )
    }
}
