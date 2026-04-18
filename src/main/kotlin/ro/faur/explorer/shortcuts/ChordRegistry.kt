package ro.faur.explorer.shortcuts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.awt.event.KeyEvent

/**
 * Singleton registry for chord-based shortcuts.
 * 
 * Stores mappings from (baseKeyCode, secondKeyCode, context) → ChordAction.
 * Supports user overrides via settings persistence.
 */
@State(
    name = "ro.faur.explorer.shortcuts.ChordRegistry",
    storages = [Storage("shortcuts.xml")]
)
class ChordRegistry : PersistentStateComponent<ChordRegistry.State> {

    data class State(
        /** Map of "baseKeyCode:secondKeyCode:context" → actionId for user overrides */
        var userOverrides: MutableMap<String, String> = mutableMapOf()
    )

    private var myState = State()

    /** Default chord mappings: (baseKeyCode, secondKeyCode, context) → ChordAction */
    private val defaultMappings = mutableMapOf<Triple<Int, Int, PanelContext>, ChordAction>()
    
    /** User-overridden mappings (takes precedence over defaults) */
    private val userMappings = mutableMapOf<Triple<Int, Int, PanelContext>, ChordAction>()

    /** All registered actions by actionId */
    private val actionsById = mutableMapOf<String, ChordAction>()

    init {
        registerDefaultMappings()
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        rebuildUserMappings()
    }

    /**
     * Registers a default chord mapping.
     * User overrides take precedence over these defaults.
     */
    fun registerDefault(action: ChordAction) {
        val key = Triple(action.baseKeyCode, action.secondKeyCode, action.context)
        defaultMappings[key] = action
        actionsById[action.actionId] = action
    }

    /**
     * Looks up the action for a chord in a given context.
     * Returns null if no action is registered.
     */
    fun getAction(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext): ChordAction? {
        val key = Triple(baseKeyCode, secondKeyCode, context)
        // User overrides take precedence
        return userMappings[key] ?: defaultMappings[key]
    }

    /**
     * Looks up an action by its ID.
     */
    fun getActionById(actionId: String): ChordAction? = actionsById[actionId]

    /**
     * Gets all actions registered for a context.
     */
    fun getActionsForContext(context: PanelContext): List<ChordAction> {
        val allMappings = (defaultMappings.keys + userMappings.keys)
            .filter { it.third == context }
            .mapNotNull { userMappings[it] ?: defaultMappings[it] }
        return allMappings.distinctBy { it.actionId }
    }

    /**
     * Gets all registered actions.
     */
    fun getAllActions(): Collection<ChordAction> = actionsById.values

    /**
     * Detects conflicts for a chord in a given context.
     * Returns list of existing actions that would conflict.
     */
    fun getConflicts(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext): List<ChordAction> {
        val key = Triple(baseKeyCode, secondKeyCode, context)
        val result = mutableListOf<ChordAction>()
        
        // Check both maps
        defaultMappings[key]?.let { result.add(it) }
        userMappings[key]?.let { result.add(it) }
        
        return result
    }

    /**
     * Checks if a chord is registered in any context.
     */
    fun isChordUsed(baseKeyCode: Int, secondKeyCode: Int): Boolean {
        return defaultMappings.keys.any { it.first == baseKeyCode && it.second == secondKeyCode } ||
               userMappings.keys.any { it.first == baseKeyCode && it.second == secondKeyCode }
    }

    /**
     * Propagates an action's shortcut to all contexts where it makes sense.
     * For example, "copy" exists in LOCAL_BROWSER, REMOTE_BROWSER, GIT_PANEL.
     */
    fun propagateToAllContexts(actionId: String, contexts: List<PanelContext>) {
        val baseAction = actionsById[actionId] ?: return
        for (context in contexts) {
            val newAction = baseAction.copy(context = context)
            registerDefault(newAction)
        }
    }

    /**
     * Returns the keycode string representation for display.
     */
    fun keyCodeToDisplayString(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.VK_BACK_QUOTE -> "`"
            KeyEvent.VK_C -> "c"
            KeyEvent.VK_X -> "x"
            KeyEvent.VK_V -> "v"
            KeyEvent.VK_D -> "d"
            KeyEvent.VK_R -> "r"
            KeyEvent.VK_N -> "n"
            KeyEvent.VK_F -> "f"
            KeyEvent.VK_O -> "o"
            KeyEvent.VK_E -> "e"
            KeyEvent.VK_P -> "p"
            KeyEvent.VK_T -> "t"
            KeyEvent.VK_Y -> "y"
            KeyEvent.VK_SLASH -> "/"
            KeyEvent.VK_SHIFT -> "Shift"
            KeyEvent.VK_CONTROL -> "Ctrl"
            KeyEvent.VK_META -> "Meta"
            KeyEvent.VK_ALT -> "Alt"
            else -> KeyEvent.getKeyText(keyCode)
        }
    }

    private fun registerDefaultMappings() {
        // File browser actions (shared between Local and Remote)
        val fileBrowserContexts = listOf(PanelContext.LOCAL_BROWSER, PanelContext.REMOTE_BROWSER)
        
        for (context in fileBrowserContexts) {
            registerDefault(ChordAction.backtick(KeyEvent.VK_C, "copy", "Copy", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_X, "cut", "Cut", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_V, "paste", "Paste", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_D, "delete", "Delete", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_R, "rename", "Rename", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_N, "newFile", "New File", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_F, "refresh", "Refresh", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_O, "open", "Open", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_E, "editInIde", "Edit in IDE", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_P, "showInTerminal", "Show in Terminal", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_T, "showInExplorer", "Show in Explorer", context, ActionCategory.FILE_OPS))
            registerDefault(ChordAction.backtick(KeyEvent.VK_Y, "copyPath", "Copy Path", context, ActionCategory.FILE_OPS))
        }
        
        // Local browser specific
        registerDefault(ChordAction.backtick(KeyEvent.VK_N, "newFolder", "New Folder", PanelContext.LOCAL_BROWSER, ActionCategory.FILE_OPS))
        
        // Git panel actions
        registerDefault(ChordAction.backtick(KeyEvent.VK_C, "cherryPick", "Cherry-pick", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_X, "revertChanges", "Revert Changes", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_R, "renameBranch", "Rename Branch", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_N, "newBranch", "New Branch", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_F, "fetch", "Fetch", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_P, "pull", "Pull", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_Y, "copyCommitHash", "Copy Commit Hash", PanelContext.GIT_PANEL, ActionCategory.GIT))
        registerDefault(ChordAction.backtick(KeyEvent.VK_Y.toInt() - 32, "copyBranchName", "Copy Branch Name", PanelContext.GIT_PANEL, ActionCategory.GIT)) // VK_Y with shift
        
        // Quick Open actions
        registerDefault(ChordAction.backtick(KeyEvent.VK_C, "copyPathQuickOpen", "Copy Path", PanelContext.QUICK_OPEN, ActionCategory.QUICK_OPEN))
        registerDefault(ChordAction.backtick(KeyEvent.VK_O, "openQuickOpen", "Open", PanelContext.QUICK_OPEN, ActionCategory.QUICK_OPEN))
        registerDefault(ChordAction.backtick(KeyEvent.VK_E, "editPathQuickOpen", "Edit Path", PanelContext.QUICK_OPEN, ActionCategory.QUICK_OPEN))
        registerDefault(ChordAction.backtick(KeyEvent.VK_Y, "copyResultQuickOpen", "Copy Result", PanelContext.QUICK_OPEN, ActionCategory.QUICK_OPEN))
        registerDefault(ChordAction.backtick(KeyEvent.VK_F, "refreshIndexQuickOpen", "Refresh Index", PanelContext.QUICK_OPEN, ActionCategory.QUICK_OPEN))
        
        // Reference panel toggle (all contexts) - VK_SLASH for '?'
        for (context in PanelContext.entries) {
            registerDefault(ChordAction.backtick(KeyEvent.VK_SLASH, "showShortcuts", "Show Shortcuts", context, ActionCategory.PANEL_OPS))
        }
    }

    private fun rebuildUserMappings() {
        userMappings.clear()
        for ((key, actionId) in myState.userOverrides) {
            val parts = key.split(":")
            if (parts.size == 3) {
                val baseKeyCode = parts[0].toIntOrNull() ?: continue
                val secondKeyCode = parts[1].toIntOrNull() ?: continue
                val context = PanelContext.entries.find { it.name == parts[2] } ?: continue
                val baseAction = actionsById[actionId] ?: continue
                val keyTriple = Triple(baseKeyCode, secondKeyCode, context)
                userMappings[keyTriple] = baseAction.copy(isUserOverride = true)
            }
        }
    }

    companion object {
        fun getInstance(): ChordRegistry =
            ApplicationManager.getApplication().getService(ChordRegistry::class.java)
    }
}
