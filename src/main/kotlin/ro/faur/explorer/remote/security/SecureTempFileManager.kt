package ro.faur.explorer.remote.security

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class SecureTempFileManager {

    private data class TempFileInfo(val host: String, val remotePath: String)

    private val tracked = ConcurrentHashMap<Path, TempFileInfo>()

    private val baseDir: Path = Paths.get(System.getProperty("user.home"), ".cache", "system-explorer", "temp")

    private val isUnix: Boolean = System.getProperty("os.name").lowercase().let {
        "linux" in it || "mac" in it
    }

    private val dirPerms = PosixFilePermissions.asFileAttribute(
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        )
    )

    private val filePerms = PosixFilePermissions.asFileAttribute(
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
    )

    fun createTempFile(host: String, remotePath: String): Path {
        // Return existing path if already tracked for same host+remotePath
        for ((path, info) in tracked) {
            if (info.host == host && info.remotePath == remotePath) {
                return path
            }
        }

        val hash = hashRemotePath(remotePath)
        val filename = remotePath.substringAfterLast('/')
            .ifEmpty { "file" }

        val hostDir = ensureSecureDir(baseDir.resolve(sanitizeHost(host)))
        val hashDir = ensureSecureDir(hostDir.resolve(hash))

        val filePath = hashDir.resolve(filename)

        return try {
            Files.newOutputStream(filePath, StandardOpenOption.CREATE_NEW).close()
            if (isUnix) {
                Files.setPosixFilePermissions(
                    filePath,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            }
            tracked[filePath] = TempFileInfo(host, remotePath)
            filePath
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            tracked[filePath] = TempFileInfo(host, remotePath)
            filePath
        }
    }

    fun cleanupAll() {
        val secure = try {
            ro.faur.explorer.remote.settings.RemoteExplorerSettings.getInstance().state.secureDeleteSensitiveFiles
        } catch (_: Exception) { false }
        val paths = tracked.keys.toList()
        tracked.clear()
        for (path in paths) {
            if (secure) {
                try {
                    val size = Files.size(path)
                    if (size > 0) {
                        Files.newOutputStream(path).use { out ->
                            val zeros = ByteArray(minOf(size, 65536L).toInt())
                            var remaining = size
                            while (remaining > 0) {
                                val chunk = minOf(remaining, zeros.size.toLong()).toInt()
                                out.write(zeros, 0, chunk)
                                remaining -= chunk
                            }
                        }
                    }
                } catch (_: Exception) { /* best-effort */ }
            }
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
            }
            val parent = path.parent
            try {
                val remaining = parent?.let { Files.list(it).count() } ?: 1L
                if (remaining == 0L) {
                    Files.deleteIfExists(parent)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getRemotePath(localPath: Path): String? = tracked[localPath]?.remotePath

    fun getHost(localPath: Path): String? = tracked[localPath]?.host

    fun isTrackedTempFile(path: Path): Boolean = tracked.containsKey(path)

    private fun ensureSecureDir(dir: Path): Path {
        if (!Files.exists(dir)) {
            if (isUnix) {
                Files.createDirectories(dir, dirPerms)
                // createDirectories may not apply attributes to intermediate dirs; set explicitly
                Files.setPosixFilePermissions(
                    dir,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                    )
                )
            } else {
                Files.createDirectories(dir)
            }
        } else if (isUnix) {
            Files.setPosixFilePermissions(
                dir,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
            )
        }
        return dir
    }

    private fun hashRemotePath(remotePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(remotePath.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun sanitizeHost(host: String): String =
        host.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
