package com.github.stefanfaur.explorer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.github.stefanfaur.explorer.settings.ExplorerSettings",
    storages = [Storage("explorerSettings.xml")]
)
class ExplorerSettings : PersistentStateComponent<ExplorerSettings.State> {

    data class State(
        var showHiddenFiles: Boolean = false,
        var sortFoldersFirst: Boolean = true,
        var confirmDelete: Boolean = true,
        var deleteToTrash: Boolean = true,
        var defaultRoot: String = "",
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
