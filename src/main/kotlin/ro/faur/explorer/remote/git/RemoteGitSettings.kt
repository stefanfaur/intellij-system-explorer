package ro.faur.explorer.remote.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "RemoteGitSettings",
    storages = [Storage("remoteGitSettings.xml")]
)
class RemoteGitSettings : PersistentStateComponent<RemoteGitSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var statusRefreshIntervalSec: Int = 30,
        var commandTimeoutSec: Int = 15,
        var showGitColorsOnTree: Boolean = true,
        var showBranchInStatusBar: Boolean = true,
        var maxBlameLines: Int = 5000,
        var disableOnRepoSizeFiles: Int = 0,  // 0 = never disable
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): RemoteGitSettings =
            ApplicationManager.getApplication().getService(RemoteGitSettings::class.java)
    }
}
