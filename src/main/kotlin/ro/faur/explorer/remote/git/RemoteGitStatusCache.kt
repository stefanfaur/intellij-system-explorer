package ro.faur.explorer.remote.git

import java.util.concurrent.ConcurrentHashMap

object RemoteGitStatusCache {
    // key: "$connectionName:$repoPath" -> map of (relativePath -> GitFileStatus)
    private val cache = ConcurrentHashMap<String, Map<String, GitFileStatus>>()

    fun update(connectionName: String, repoPath: String, statusLines: List<String>) {
        val key = "$connectionName:$repoPath"
        val statusMap = mutableMapOf<String, GitFileStatus>()
        for (line in statusLines) {
            val entry = GitStatusParser.parseLine(line) ?: continue
            statusMap[entry.relativePath] = entry.status
        }
        cache[key] = statusMap
    }

    fun getStatus(connectionName: String, filePath: String): GitFileStatus? {
        for ((key, statusMap) in cache) {
            if (key.startsWith("$connectionName:")) {
                val repoPath = key.removePrefix("$connectionName:")
                val relativePath = if (filePath.startsWith(repoPath)) {
                    filePath.removePrefix(repoPath).trimStart('/')
                } else {
                    filePath
                }
                statusMap[relativePath]?.let { return it }
            }
        }
        return null
    }

    fun invalidate(connectionName: String) {
        cache.keys.removeAll { it.startsWith("$connectionName:") }
    }

    fun clear() {
        cache.clear()
    }
}
