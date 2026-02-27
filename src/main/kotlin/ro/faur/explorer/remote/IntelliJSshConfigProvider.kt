package ro.faur.explorer.remote

import com.intellij.openapi.diagnostic.Logger

/**
 * Reads SSH configurations from IntelliJ's built-in SSH plugin (if available).
 *
 * This is the lowest-priority connection source:
 * 1. Plugin-defined profiles ([RemoteConnectionSettings])
 * 2. ~/.ssh/config ([SshConfigParser])
 * 3. IntelliJ SSH configurations (this class)
 *
 * Uses reflection to access `com.intellij.ssh.config.SshConfigManager` so the
 * plugin compiles and runs even when the IntelliJ SSH plugin is not installed.
 */
object IntelliJSshConfigProvider {

    private val LOG = Logger.getInstance(IntelliJSshConfigProvider::class.java)

    private const val SSH_CONFIG_MANAGER_CLASS = "com.intellij.ssh.config.unified.SshConfigManager"

    /**
     * Returns [ConnectionProfile] instances derived from IntelliJ's SSH configurations.
     * Returns an empty list if the SSH plugin is not available or if reading fails.
     */
    fun getProfiles(): List<ConnectionProfile> {
        return try {
            loadProfilesViaReflection()
        } catch (e: ClassNotFoundException) {
            LOG.info("IntelliJ SSH plugin not available — skipping IDE SSH configs")
            emptyList()
        } catch (e: Exception) {
            LOG.warn("Failed to read IntelliJ SSH configurations", e)
            emptyList()
        }
    }

    /**
     * Reflectively loads SSH configs from the IntelliJ SSH plugin.
     *
     * The IntelliJ SSH plugin exposes configurations through SshConfigManager.
     * Each config has host, port, username, authType, and keyPath fields.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadProfilesViaReflection(): List<ConnectionProfile> {
        val managerClass = Class.forName(SSH_CONFIG_MANAGER_CLASS)

        // SshConfigManager.getInstance() — static / companion method
        val getInstanceMethod = managerClass.getMethod("getInstance")
        val manager = getInstanceMethod.invoke(null)
            ?: return emptyList()

        // manager.getConfigs() or manager.configs — returns a collection of SSH config objects
        val configs: Collection<Any> = try {
            val getConfigsMethod = managerClass.getMethod("getConfigs")
            getConfigsMethod.invoke(manager) as? Collection<Any> ?: emptyList()
        } catch (_: NoSuchMethodException) {
            // Try property-style accessor
            try {
                val configsField = managerClass.getMethod("getConfigsList")
                configsField.invoke(manager) as? Collection<Any> ?: emptyList()
            } catch (_: NoSuchMethodException) {
                LOG.info("Could not find getConfigs/getConfigsList on SshConfigManager — API may have changed")
                return emptyList()
            }
        }

        return configs.mapNotNull { config -> extractProfile(config) }
    }

    /**
     * Extracts a [ConnectionProfile] from a single IntelliJ SSH config object via reflection.
     * Returns null if the config cannot be parsed or has no host.
     */
    private fun extractProfile(config: Any): ConnectionProfile? {
        return try {
            val configClass = config.javaClass

            val host = getStringProperty(config, configClass, "host", "getHost")
                ?: return null
            if (host.isBlank()) return null

            val port = getIntProperty(config, configClass, "port", "getPort") ?: 22
            val username = getStringProperty(config, configClass, "userName", "getUserName")
                ?: getStringProperty(config, configClass, "username", "getUsername")
                ?: ""

            val configName = getStringProperty(config, configClass, "name", "getName")
                ?: getStringProperty(config, configClass, "id", "getId")
                ?: "IntelliJ: $username@$host"

            val keyPath = getStringProperty(config, configClass, "keyPath", "getKeyPath")
                ?: getStringProperty(config, configClass, "privateKeyFile", "getPrivateKeyFile")

            val authMethod = determineAuthMethod(config, configClass, keyPath)

            ConnectionProfile(
                name = configName,
                host = host,
                port = port,
                username = username,
                authMethod = authMethod,
                keyFilePath = keyPath,
            )
        } catch (e: Exception) {
            LOG.debug("Failed to extract profile from IntelliJ SSH config: ${e.message}")
            null
        }
    }

    private fun determineAuthMethod(
        config: Any,
        configClass: Class<*>,
        keyPath: String?,
    ): ConnectionProfile.AuthMethod {
        // Try to read authType enum
        val authType = getStringProperty(config, configClass, "authType", "getAuthType")
        return when {
            authType != null && authType.uppercase().contains("PASSWORD") ->
                ConnectionProfile.AuthMethod.PASSWORD
            authType != null && authType.uppercase().contains("KEY") ->
                ConnectionProfile.AuthMethod.KEY_FILE
            authType != null && authType.uppercase().contains("AGENT") ->
                ConnectionProfile.AuthMethod.AGENT
            keyPath != null && keyPath.isNotBlank() ->
                ConnectionProfile.AuthMethod.KEY_FILE
            else ->
                ConnectionProfile.AuthMethod.AGENT
        }
    }

    /** Attempts to read a String property by trying getter methods and direct field access. */
    private fun getStringProperty(obj: Any, clazz: Class<*>, vararg names: String): String? {
        for (name in names) {
            try {
                val method = clazz.getMethod(name)
                val value = method.invoke(obj)
                if (value is String) return value
            } catch (_: NoSuchMethodException) {
                // Try next name
            }
        }
        // Try field access as last resort
        for (name in names) {
            if (name.startsWith("get")) continue // skip method-style names for field access
            try {
                val field = clazz.getDeclaredField(name)
                try {
                    field.isAccessible = true
                } catch (e: Exception) {
                    LOG.warn("Cannot access IntelliJ SSH config internals: ${e.message}")
                    return null
                }
                val value = field.get(obj)
                if (value is String) return value
            } catch (_: NoSuchFieldException) {
                // Try next
            }
        }
        return null
    }

    /** Attempts to read an Int property by trying getter methods. */
    private fun getIntProperty(obj: Any, clazz: Class<*>, vararg names: String): Int? {
        for (name in names) {
            try {
                val method = clazz.getMethod(name)
                val value = method.invoke(obj)
                if (value is Int) return value
                if (value is Number) return value.toInt()
            } catch (_: NoSuchMethodException) {
                // Try next name
            }
        }
        return null
    }
}
