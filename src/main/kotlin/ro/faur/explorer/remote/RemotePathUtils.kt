package ro.faur.explorer.remote

object RemotePathUtils {

    /**
     * Returns the parent directory of the given path.
     * If the path is root ("/"), returns root.
     */
    fun parentPath(path: String): String {
        if (path == "/") return "/"
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash <= 0) "/" else trimmed.substring(0, lastSlash)
    }

    /**
     * Joins a parent path and a child segment into a single path.
     * Handles trailing slashes on the parent.
     */
    fun join(parent: String, child: String): String {
        val cleanChild = child.trimStart('/')
        require(!cleanChild.contains("..")) { "Path traversal not allowed: $child" }
        return "${parent.trimEnd('/')}/$cleanChild"
    }

    /**
     * Extracts the last path segment (file or directory name).
     * Returns an empty string for root ("/").
     */
    fun fileName(path: String): String {
        if (path == "/") return ""
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash < 0) trimmed else trimmed.substring(lastSlash + 1)
    }

    /**
     * Returns true if the given file name matches sensitive file patterns:
     * - .env or .env.* files
     * - secrets.* files
     * - credentials.* files
     * - files containing "password" anywhere in the name
     *
     * Matching is case-insensitive.
     */
    fun isSensitiveFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower == ".env"
            || lower.startsWith(".env.")
            || lower.startsWith("secrets.")
            || lower.startsWith("credentials.")
            || lower.contains("password")
    }

    /**
     * Splits a remote path into breadcrumb segments.
     * The first element is always "/" representing root,
     * followed by each path component.
     * Example: "/var/www/html" -> ["/", "var", "www", "html"]
     */
    fun breadcrumbs(path: String): List<String> {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty() || trimmed == "/") return listOf("/")
        val segments = trimmed.split("/").filter { it.isNotEmpty() }
        return listOf("/") + segments
    }
}
