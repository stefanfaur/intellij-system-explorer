package ro.faur.explorer.remote.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import ro.faur.explorer.remote.ConnectionProfile

@Service(Service.Level.PROJECT)
@State(
    name = "SftpConnectionProfiles",
    storages = [Storage(value = "sftpConnections.xml", roamingType = RoamingType.LOCAL)]
)
class RemoteConnectionSettings : PersistentStateComponent<RemoteConnectionSettings.State> {

    data class State(
        var connections: MutableList<ConnectionProfile> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Add a connection profile. Returns false if a connection with the same name exists.
     */
    fun addConnection(profile: ConnectionProfile): Boolean {
        if (myState.connections.any { it.name == profile.name }) return false
        myState.connections.add(profile)
        return true
    }

    fun removeConnection(name: String) {
        myState.connections.removeAll { it.name == name }
    }

    fun getConnection(name: String): ConnectionProfile? {
        return myState.connections.find { it.name == name }
    }

    companion object {
        fun getInstance(project: Project): RemoteConnectionSettings {
            return project.getService(RemoteConnectionSettings::class.java)
        }
    }
}
