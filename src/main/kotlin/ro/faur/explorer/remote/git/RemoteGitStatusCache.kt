package ro.faur.explorer.remote.git

import java.util.concurrent.ConcurrentHashMap

object RemoteGitStatusCache {
    // key: "$connectionName:$repoPath" -> map of (relativePath -> GitFileStatus)
    private val cache = ConcurrentHashMap<String, Map<String, GitFileStatus>>()

    private fun cacheKey(connectionName: String, repoPath: String): String =
        "${connectionName.length}:$connectionName:$repoPath"

    fun update(connectionName: String, repoPath: String, statusLines: List<String>) {
        // TODO: guard against very large repos once repo file count tracking is implemented.
        // When repoFileCount is available, check RemoteGitSettings.getInstance().state.disableOnRepoSizeFiles.
        val key = cacheKey(connectionName, repoPath)
        val statusMap = mutableMapOf<String, GitFileStatus>()
        for (line in statusLines) {
            val entry = GitStatusParser.parseLine(line) ?: continue
            statusMap[entry.relativePath] = entry.status
        }
        cache[key] = statusMap
    }

    fun getStatus(connectionName: String, filePath: String): GitFileStatus? {
        val prefix = "${connectionName.length}:$connectionName:"
        for ((key, statusMap) in cache) {
            if (key.startsWith(prefix)) {
                val repoPath = key.removePrefix(prefix)
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
        val prefix = "${connectionName.length}:$connectionName:"
        cache.keys.removeAll { it.startsWith(prefix) }
    }

    fun clear() {
        cache.clear()
    }
}
