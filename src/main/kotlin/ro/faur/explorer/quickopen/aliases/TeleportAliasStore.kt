package ro.faur.explorer.quickopen.aliases

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ro.faur.explorer.quickopen.TeleportAliasStore",
    storages = [Storage("explorerAliases.xml")]
)
class TeleportAliasStore : PersistentStateComponent<TeleportAliasStore.State> {

    class AliasEntry {
        var name: String = ""
        var path: String = ""
        constructor()
        constructor(name: String, path: String) { this.name = name; this.path = path }
    }

    class State {
        var aliases: MutableList<AliasEntry> = mutableListOf()
    }

    private var myState = State()

    override fun getState() = myState
    override fun loadState(state: State) { myState = state }

    fun getAliases(): Map<String, String> =
        myState.aliases.associate { it.name to it.path }

    fun addAlias(name: String, path: String) {
        myState.aliases.removeAll { it.name == name }
        myState.aliases.add(AliasEntry(name, path))
    }

    fun removeAlias(name: String) {
        myState.aliases.removeAll { it.name == name }
    }

    companion object {
        fun getInstance(): TeleportAliasStore =
            ApplicationManager.getApplication().getService(TeleportAliasStore::class.java)
    }
}
