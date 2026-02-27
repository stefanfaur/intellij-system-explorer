package ro.faur.explorer.gitpanel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class GitRepositoryRegistry : Disposable {
    private val backends = ConcurrentHashMap<String, GitBackend>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(backend: GitBackend) {
        backends[backend.id.key] = backend
        fireChanged()
    }

    fun unregister(id: GitBackendId) {
        backends.remove(id.key)?.dispose()
        fireChanged()
    }

    fun unregisterByConnection(connectionName: String) {
        val toRemove = backends.keys.filter { it.startsWith("remote:$connectionName:") }
        toRemove.forEach { backends.remove(it)?.dispose() }
        if (toRemove.isNotEmpty()) fireChanged()
    }

    fun getAll(): List<GitBackend> = backends.values.toList()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun fireChanged() { listeners.forEach { it() } }

    override fun dispose() {
        backends.values.forEach { it.dispose() }
        backends.clear()
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): GitRepositoryRegistry =
            project.getService(GitRepositoryRegistry::class.java)
    }
}
