package ro.faur.explorer.shortcuts

import java.awt.event.KeyEvent

/**
 * Represents a chord-based action mapping.
 *
 * A chord consists of a base key (e.g., backtick) followed by a second key (e.g., 'c').
 * The ChordRegistry maps (baseKeycode, secondKeycode, context) → actionId.
 */
data class ChordAction(
    /** Unique identifier for this action (e.g., "copy", "delete", "cherry-pick") */
    val actionId: String,
    /** Human-readable display name */
    val displayName: String,
    /** Key code of the base chord key (e.g., KeyEvent.VK_BACK_QUOTE) */
    val baseKeyCode: Int,
    /** Key code of the second key (e.g., KeyEvent.VK_C) */
    val secondKeyCode: Int,
    /** The context in which this chord is active */
    val context: PanelContext,
    /** Category for grouping in the reference panel */
    val category: ActionCategory
) {
    companion object {
        const val BASE_KEY_BACKTICK = KeyEvent.VK_BACK_QUOTE

        fun backtick(secondKeyCode: Int, actionId: String, displayName: String, context: PanelContext, category: ActionCategory) =
            ChordAction(actionId, displayName, BASE_KEY_BACKTICK, secondKeyCode, context, category)
        
        // Key code for '/' which produces '?' with shift (used for help shortcut)
        const val KEY_SLASH = java.awt.event.KeyEvent.VK_SLASH
    }
}

/**
 * Categories for grouping actions in the shortcut reference panel.
 */
enum class ActionCategory {
    NAVIGATION,
    FILE_OPS,
    PANEL_OPS,
    GIT,
    QUICK_OPEN
}
