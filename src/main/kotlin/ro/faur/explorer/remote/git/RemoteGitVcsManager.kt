package ro.faur.explorer.remote.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class RemoteGitVcsManager {
    private val roots = ConcurrentHashMap<String, MutableSet<String>>()

    fun registerRoot(connectionName: String, repoPath: String) {
        roots.getOrPut(connectionName) { ConcurrentHashMap.newKeySet() }.add(repoPath)
    }

    fun unregisterRoot(connectionName: String, repoPath: String) {
        roots[connectionName]?.remove(repoPath)
    }

    fun getRoots(connectionName: String): Set<String> {
        return roots[connectionName]?.toSet() ?: emptySet()
    }

    fun clearConnection(connectionName: String) {
        roots.remove(connectionName)
    }

    fun isInsideRepo(connectionName: String, path: String): Boolean {
        return getRepoRoot(connectionName, path) != null
    }

    fun getRepoRoot(connectionName: String, path: String): String? {
        return roots[connectionName]?.find { root ->
            path == root || path.startsWith("$root/")
        }
    }

    companion object {
        fun getInstance(project: Project): RemoteGitVcsManager {
            return project.getService(RemoteGitVcsManager::class.java)
        }
    }
}
