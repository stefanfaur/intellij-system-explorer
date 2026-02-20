package ro.faur.explorer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ro.faur.explorer.settings.ExplorerSettings",
    storages = [Storage("explorerSettings.xml")]
)
class ExplorerSettings : PersistentStateComponent<ExplorerSettings.State> {

    data class State(
        var showHiddenFiles: Boolean = false,
        var sortFoldersFirst: Boolean = true,
        var confirmDelete: Boolean = true,
        var deleteToTrash: Boolean = true,
        var defaultRoot: String = "",
        var showFileSizeInTree: Boolean = true,
        var showFilePermissions: Boolean = false,
        var sortBy: String = "name",  // "name", "size", "modified"
        var expandDirectoriesOnSingleClick: Boolean = true,
        var rememberLastPath: Boolean = true,
        var quickOpenV2Enabled: Boolean = false,
        // Phase 6 — ripgrep and content search settings
        var ripgrepPath: String = "",
        var useRipgrepForExternalPaths: Boolean = false,
        var maxIndexSize: Int = 50_000,
        var allowNetworkMountIndexing: Boolean = false,
        var contentSearchEnabled: Boolean = true,
        var contentSearchScope: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): ExplorerSettings =
            ApplicationManager.getApplication().getService(ExplorerSettings::class.java)
    }
}
