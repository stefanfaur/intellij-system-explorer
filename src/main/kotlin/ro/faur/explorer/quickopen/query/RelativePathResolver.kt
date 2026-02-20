package ro.faur.explorer.quickopen.query

import java.nio.file.Paths

object RelativePathResolver {
    /**
     * If [text] looks like a relative path (starts with ./ or ../), resolve it
     * relative to [currentPath] and return the normalized absolute path.
     * Returns null if [text] is not a relative path.
     */
    fun resolve(text: String, currentPath: String): String? {
        if (!text.startsWith("./") && !text.startsWith("../") && text != "..") return null
        return try {
            Paths.get(currentPath).resolve(text).normalize().toString()
        } catch (_: Exception) { null }
    }

    /**
     * Expands path alias prefixes: ~ → user.home, $HOME → user.home
     */
    fun expandAliases(text: String): String {
        val home = System.getProperty("user.home") ?: return text
        return text
            .replace(Regex("^~"), home)
            .replace(Regex("^\\\$HOME"), home)
    }
}
