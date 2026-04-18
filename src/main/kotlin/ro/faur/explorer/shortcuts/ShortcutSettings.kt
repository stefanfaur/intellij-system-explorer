package ro.faur.explorer.shortcuts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Persistent state component for user shortcut customizations.
 * 
 * Stores user overrides that take precedence over default chord mappings.
 */
@State(
    name = "ro.faur.explorer.shortcuts.ShortcutSettings",
    storages = [Storage("shortcutSettings.xml")]
)
class ShortcutSettings : PersistentStateComponent<ShortcutSettings.State> {

    data class State(
        /** Map of "baseKeyCode:secondKeyCode:context" → actionId for user overrides */
        var userOverrides: MutableMap<String, String> = mutableMapOf(),
        /** Active shortcut profile ID */
        var activeProfileId: String = DEFAULT_PROFILE_ID,
        /** List of custom profiles */
        var profiles: MutableList<Profile> = mutableListOf(),
        /** Reference panel auto-hide delay in seconds */
        var referencePanelAutoHideDelay: Int = 5,
        /** Whether reference panel is visible */
        var referencePanelVisible: Boolean = false
    )

    data class Profile(
        val id: String,
        val name: String,
        val shortcuts: MutableMap<String, String> = mutableMapOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Gets a user override for a chord.
     */
    fun getOverride(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext): String? {
        val key = "${baseKeyCode}:${secondKeyCode}:${context.name}"
        return myState.userOverrides[key]
    }

    /**
     * Sets a user override for a chord.
     */
    fun setOverride(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext, actionId: String) {
        val key = "${baseKeyCode}:${secondKeyCode}:${context.name}"
        myState.userOverrides[key] = actionId
    }

    /**
     * Clears a user override.
     */
    fun clearOverride(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext) {
        val key = "${baseKeyCode}:${secondKeyCode}:${context.name}"
        myState.userOverrides.remove(key)
    }

    /**
     * Clears all user overrides.
     */
    fun clearAllOverrides() {
        myState.userOverrides.clear()
    }

    /**
     * Gets all user overrides.
     */
    fun getAllOverrides(): Map<String, String> = myState.userOverrides.toMap()

    /**
     * Exports settings to JSON file.
     */
    fun exportToJson(file: File): Boolean {
        return try {
            val json = Gson().toJson(myState)
            file.writeText(json)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Imports settings from JSON file.
     */
    fun importFromJson(file: File): Boolean {
        return try {
            val json = file.readText()
            val importedState = Gson().fromJson(json, State::class.java)
            myState = importedState
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the active profile.
     */
    fun getActiveProfile(): Profile? {
        return myState.profiles.find { it.id == myState.activeProfileId }
    }

    /**
     * Sets the active profile.
     */
    fun setActiveProfile(profileId: String) {
        myState.activeProfileId = profileId
    }

    /**
     * Adds a new profile.
     */
    fun addProfile(profile: Profile) {
        myState.profiles.add(profile)
    }

    /**
     * Removes a profile.
     */
    fun removeProfile(profileId: String) {
        myState.profiles.removeAll { it.id == profileId }
        if (myState.activeProfileId == profileId) {
            myState.activeProfileId = DEFAULT_PROFILE_ID
        }
    }

    /**
     * Updates shortcuts in the active profile.
     */
    fun updateProfileShortcuts(shortcuts: Map<String, String>) {
        val profile = getActiveProfile() ?: return
        profile.shortcuts.clear()
        profile.shortcuts.putAll(shortcuts)
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "default"
        
        fun getInstance(): ShortcutSettings =
            ApplicationManager.getApplication().getService(ShortcutSettings::class.java)
    }
}
