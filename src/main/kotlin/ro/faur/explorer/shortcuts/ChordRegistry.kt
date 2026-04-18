package ro.faur.explorer.shortcuts

import com.intellij.openapi.application.ApplicationManager
import java.awt.event.KeyEvent

/**
 * Singleton registry for chord-based shortcuts.
 *
 * Stores mappings from (baseKeyCode, secondKeyCode, context) → ChordAction.
 * Mappings are hard-coded defaults — no user customization.
 */
class ChordRegistry {

    /** Default chord mappings: (baseKeyCode, secondKeyCode, context) → ChordAction */
    private val defaultMappings = mutableMapOf<Triple<Int, Int, PanelContext>, ChordAction>()

    /** All registered actions by actionId */
    private val actionsById = mutableMapOf<String, ChordAction>()

    init {
        registerDefaultMappings()
    }

    private fun registerDefault(action: ChordAction) {
        val key = Triple(action.baseKeyCode, action.secondKeyCode, action.context)
        defaultMappings[key] = action
        actionsById[action.actionId] = action
    }

    /**
     * Looks up the action for a chord in a given context.
     * Returns null if no action is registered.
     */
    fun getAction(baseKeyCode: Int, secondKeyCode: Int, context: PanelContext): ChordAction? {
        return defaultMappings[Triple(baseKeyCode, secondKeyCode, context)]
    }

    fun getActionById(actionId: String): ChordAction? = actionsById[actionId]

    fun getActionsForContext(context: PanelContext): List<ChordAction> {
        return defaultMappings.values
            .filter { it.context == context }
            .distinctBy { it.actionId }
    }

    fun getAllActions(): Collection<ChordAction> = actionsById.values

    /**
     * Checks if a chord is registered in any context.
     */
    fun isChordUsed(baseKeyCode: Int, secondKeyCode: Int): Boolean {
        return defaultMappings.keys.any { it.first == baseKeyCode && it.second == secondKeyCode }
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

    companion object {
        fun getInstance(): ChordRegistry =
            ApplicationManager.getApplication().getService(ChordRegistry::class.java)
    }
}
