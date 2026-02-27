package ro.faur.explorer.remote.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "RemoteExplorerSettings",
    storages = [Storage("remoteExplorerSettings.xml")]
)
class RemoteExplorerSettings : PersistentStateComponent<RemoteExplorerSettings.State> {

    data class State(
        var keepaliveIntervalSec: Int = 60,
        var connectionTimeoutSec: Int = 10,
        var directoryCacheTtlSec: Int = 30,
        var maxFileSizeMb: Int = 10,
        var tailInitialLines: Int = 1000,
        var showSshConfigHosts: Boolean = true,
        var secureDeleteSensitiveFiles: Boolean = true,
        var keepaliveMaxFailures: Int = 3,
        var connectionRetryAttempts: Int = 2,
        var maxCachedDirectories: Int = 200,
        var maxTransferSizeMb: Long = 100L,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): RemoteExplorerSettings =
            ApplicationManager.getApplication().getService(RemoteExplorerSettings::class.java)
    }
}
