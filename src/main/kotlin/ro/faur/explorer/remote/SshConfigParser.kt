package ro.faur.explorer.remote

import java.nio.file.Files
import java.nio.file.Path

/**
 * Parsed entry from an SSH config file (~/.ssh/config).
 */
data class SshConfigEntry(
    val alias: String,
    val hostName: String,
    val port: Int = 22,
    val username: String? = null,
    val identityFile: String? = null,
    val proxyJump: String? = null,
)

/**
 * Parses SSH config files (~/.ssh/config) to discover configured hosts.
 * Read-only — never modifies the SSH config.
 *
 * Ignores wildcard Host entries (e.g., `Host *`) since they are global defaults,
 * not specific connection targets.
 */
object SshConfigParser {

    fun parse(configPath: Path): List<SshConfigEntry> {
        if (!Files.exists(configPath)) return emptyList()

        val content = Files.readString(configPath)
        if (content.isBlank()) return emptyList()

        val entries = mutableListOf<SshConfigEntry>()
        var currentAlias: String? = null
        var hostName: String? = null
        var port: Int = 22
        var username: String? = null
        var identityFile: String? = null
        var proxyJump: String? = null

        fun flushEntry() {
            val alias = currentAlias ?: return
            if (alias.contains('*') || alias.contains('?')) {
                // Skip wildcard entries
                currentAlias = null
                return
            }
            entries.add(
                SshConfigEntry(
                    alias = alias,
                    hostName = hostName ?: alias,
                    port = port,
                    username = username,
                    identityFile = identityFile,
                    proxyJump = proxyJump,
                )
            )
            currentAlias = null
            hostName = null
            port = 22
            username = null
            identityFile = null
            proxyJump = null
        }

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val parts = line.split("\\s+".toRegex(), limit = 2)
            if (parts.size < 2) continue

            val key = parts[0]
            val value = parts[1]

            when (key.lowercase()) {
                "host" -> {
                    flushEntry()
                    currentAlias = value
                }
                "hostname" -> hostName = value
                "port" -> port = value.toIntOrNull() ?: 22
                "user" -> username = value
                "identityfile" -> {
                    val expanded = value.replaceFirst("~", System.getProperty("user.home"))
                        .replace("%d", System.getProperty("user.home"))
                        .replace("%h", hostName ?: currentAlias ?: "")
                    identityFile = expanded
                }
                "proxyjump" -> proxyJump = value
            }
        }

        flushEntry()
        return entries
    }
}
