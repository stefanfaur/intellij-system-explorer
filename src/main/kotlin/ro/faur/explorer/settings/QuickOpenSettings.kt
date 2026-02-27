package ro.faur.explorer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "ro.faur.explorer.settings.QuickOpenSettings",
    storages = [Storage("explorerQuickOpen.xml")]
)
class QuickOpenSettings : PersistentStateComponent<QuickOpenSettings.State> {

    data class State(
        // Index & limits
        var maxIndexSize: Int = 50_000,
        var maxDisplayedResults: Int = 50,
        var speedDialCount: Int = 6,
        var recentQueryHistory: Int = 20,
        var indexEnumerationTimeoutSec: Int = 10,
        // Content search
        var ripgrepPath: String = "",
        var maxContentResults: Int = 200,
        var ripgrepSearchHidden: Boolean = false,
        var ripgrepFollowSymlinks: Boolean = false,
        var ripgrepRespectIgnore: Boolean = true,
        var ripgrepMaxDepth: Int = 0,
        var ripgrepExtraFlags: String = "",
        var useRipgrepForExternalPaths: Boolean = false,
        var contentSearchEnabled: Boolean = true,
        // Scoring & frecency
        var frecencyHalfLifeHours: Double = 24.0,
        var frecencyRecencyWeight: Int = 60,   // 0–100; freq weight = 100 - this, then scaled
        var showScorerDebug: Boolean = false,
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): QuickOpenSettings =
            ApplicationManager.getApplication().getService(QuickOpenSettings::class.java)
    }
}
