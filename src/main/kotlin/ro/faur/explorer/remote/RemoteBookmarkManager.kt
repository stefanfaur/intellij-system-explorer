package ro.faur.explorer.remote

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "RemoteBookmarks",
    storages = [Storage(value = "remoteBookmarks.xml", roamingType = RoamingType.LOCAL)]
)
class RemoteBookmarkManager : PersistentStateComponent<RemoteBookmarkManager.State> {

    data class State(
        var bookmarksByConnection: MutableMap<String, MutableList<RemoteBookmark>> = mutableMapOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun addBookmark(connectionName: String, bookmark: RemoteBookmark) {
        myState.bookmarksByConnection
            .getOrPut(connectionName) { mutableListOf() }
            .add(bookmark)
    }

    fun removeBookmark(connectionName: String, path: String) {
        myState.bookmarksByConnection[connectionName]?.removeAll { it.path == path }
    }

    fun getBookmarks(connectionName: String): List<RemoteBookmark> {
        return myState.bookmarksByConnection[connectionName]?.toList() ?: emptyList()
    }

    fun getConnectionNames(): Set<String> {
        return myState.bookmarksByConnection.keys.toSet()
    }

    fun clearConnection(connectionName: String) {
        myState.bookmarksByConnection.remove(connectionName)
    }

    fun clearAll() {
        myState.bookmarksByConnection.clear()
    }

    companion object {
        fun getInstance(project: Project): RemoteBookmarkManager {
            return project.getService(RemoteBookmarkManager::class.java)
        }
    }
}
