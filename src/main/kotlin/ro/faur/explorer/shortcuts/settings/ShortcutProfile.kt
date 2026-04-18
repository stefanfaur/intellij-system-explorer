package ro.faur.explorer.shortcuts.settings

import ro.faur.explorer.shortcuts.ActionCategory
import ro.faur.explorer.shortcuts.PanelContext
import java.awt.event.KeyEvent

/**
 * Represents a named collection of shortcut bindings.
 * 
 * Profiles allow users to switch between different shortcut configurations
 * (e.g., "Default", "Vim-inspired", "VS Code-like").
 */
data class ShortcutProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val isBuiltIn: Boolean = false,
    val bindings: MutableMap<ShortcutKey, String> = mutableMapOf()
) {
    /**
     * Gets the action ID for a given shortcut key in this profile.
     * Returns null if no binding exists.
     */
    fun getBinding(key: ShortcutKey): String? = bindings[key]

    /**
     * Sets a shortcut binding.
     */
    fun setBinding(key: ShortcutKey, actionId: String) {
        bindings[key] = actionId
    }

    /**
     * Removes a shortcut binding.
     */
    fun removeBinding(key: ShortcutKey) {
        bindings.remove(key)
    }

    companion object {
        /**
         * Creates the default built-in profile.
         */
        fun createDefaultProfile(): ShortcutProfile = ShortcutProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Default",
            description = "Default System Explorer shortcuts",
            isBuiltIn = true
        ).apply {
            bindings.putAll(createDefaultBindings())
        }

        /**
         * Creates the default chord bindings for all contexts.
         */
        fun createDefaultBindings(): Map<ShortcutKey, String> {
            val bindings = mutableMapOf<ShortcutKey, String>()

            // Local and Remote file browser shortcuts
            val fileBrowserContexts = listOf(PanelContext.LOCAL_BROWSER, PanelContext.REMOTE_BROWSER)
            for (context in fileBrowserContexts) {
                bindings[ShortcutKey.chord('c', context)] = "copy"
                bindings[ShortcutKey.chord('x', context)] = "cut"
                bindings[ShortcutKey.chord('v', context)] = "paste"
                bindings[ShortcutKey.chord('d', context)] = "delete"
                bindings[ShortcutKey.chord('r', context)] = "rename"
                bindings[ShortcutKey.chord('n', context)] = "newFile"
                bindings[ShortcutKey.chord('f', context)] = "refresh"
                bindings[ShortcutKey.chord('o', context)] = "open"
                bindings[ShortcutKey.chord('e', context)] = "editInIde"
                bindings[ShortcutKey.chord('p', context)] = "showInTerminal"
                bindings[ShortcutKey.chord('t', context)] = "showInExplorer"
                bindings[ShortcutKey.chord('y', context)] = "copyPath"
            }

            // Local browser specific
            bindings[ShortcutKey.chord('N', PanelContext.LOCAL_BROWSER)] = "newFolder"

            // Git panel shortcuts
            bindings[ShortcutKey.chord('c', PanelContext.GIT_PANEL)] = "cherryPick"
            bindings[ShortcutKey.chord('x', PanelContext.GIT_PANEL)] = "revertChanges"
            bindings[ShortcutKey.chord('r', PanelContext.GIT_PANEL)] = "renameBranch"
            bindings[ShortcutKey.chord('n', PanelContext.GIT_PANEL)] = "newBranch"
            bindings[ShortcutKey.chord('f', PanelContext.GIT_PANEL)] = "fetch"
            bindings[ShortcutKey.chord('p', PanelContext.GIT_PANEL)] = "pull"
            bindings[ShortcutKey.chord('y', PanelContext.GIT_PANEL)] = "copyCommitHash"

            // Quick Open shortcuts
            bindings[ShortcutKey.chord('c', PanelContext.QUICK_OPEN)] = "copyPathQuickOpen"
            bindings[ShortcutKey.chord('o', PanelContext.QUICK_OPEN)] = "openQuickOpen"
            bindings[ShortcutKey.chord('e', PanelContext.QUICK_OPEN)] = "editPathQuickOpen"
            bindings[ShortcutKey.chord('y', PanelContext.QUICK_OPEN)] = "copyResultQuickOpen"
            bindings[ShortcutKey.chord('f', PanelContext.QUICK_OPEN)] = "refreshIndexQuickOpen"

            // Reference panel toggle (all contexts)
            for (context in PanelContext.entries) {
                bindings[ShortcutKey.chord('/', context)] = "showShortcuts"
            }

            return bindings
        }

        const val DEFAULT_PROFILE_ID = "default"
    }
}

/**
 * Represents a unique shortcut key (base key + second key + context).
 */
data class ShortcutKey(
    val baseKeyCode: Int,
    val secondKeyCode: Int,
    val context: PanelContext
) {
    companion object {
        const val BASE_KEY = KeyEvent.VK_BACK_QUOTE

        /**
         * Creates a chord shortcut key from a character.
         */
        fun chord(secondKey: Char, context: PanelContext): ShortcutKey {
            val keyCode = if (secondKey.isUpperCase()) {
                secondKey.code - 'A'.code + KeyEvent.VK_A
            } else {
                secondKey.lowercaseChar().code - 'a'.code + KeyEvent.VK_A
            }
            return ShortcutKey(BASE_KEY, keyCode, context)
        }

        /**
         * Creates a chord shortcut key from a key code.
         */
        fun chord(secondKeyCode: Int, context: PanelContext): ShortcutKey {
            return ShortcutKey(BASE_KEY, secondKeyCode, context)
        }

        /**
         * Creates a key for '/' (help shortcut).
         */
        fun help(context: PanelContext): ShortcutKey {
            return ShortcutKey(BASE_KEY, KeyEvent.VK_SLASH, context)
        }
    }

    /**
     * Returns a human-readable representation of this shortcut.
     */
    fun toDisplayString(): String {
        val base = if (baseKeyCode == KeyEvent.VK_BACK_QUOTE) "`" else KeyEvent.getKeyText(baseKeyCode)
        val second = if (secondKeyCode == KeyEvent.VK_SLASH) "/" else {
            if (secondKeyCode in KeyEvent.VK_A..KeyEvent.VK_Z) {
                val char = ('a' + (secondKeyCode - KeyEvent.VK_A))
                char.toString()
            } else {
                KeyEvent.getKeyText(secondKeyCode)
            }
        }
        return "$base$second"
    }

    /**
     * Returns the category for the action bound to this shortcut.
     */
    fun getCategory(): ActionCategory {
        return when (context) {
            PanelContext.LOCAL_BROWSER, PanelContext.REMOTE_BROWSER -> ActionCategory.FILE_OPS
            PanelContext.GIT_PANEL -> ActionCategory.GIT
            PanelContext.QUICK_OPEN -> ActionCategory.QUICK_OPEN
            PanelContext.UNKNOWN -> ActionCategory.PANEL_OPS
        }
    }
}
