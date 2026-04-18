package ro.faur.explorer.shortcuts

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.keymap.KeymapManager
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * Helper utilities for adding keyboard shortcut hints to context menu items.
 * 
 * Usage:
 * ```kotlin
 * // Wrap an action with its shortcut hint displayed
 * val action = ShortcutHintHelper.wrapWithHint(
 *     myAction,
 *     Presentation("Action Name"),
 *     "my.action.id"
 * )
 * 
 * // Or use the extension function
 * val action = myAction.withShortcutHint("Copy Path", "SystemExplorer.CopyPath")
 * ```
 */
object ShortcutHintHelper {

    /**
     * Gets the keyboard shortcut text for an action ID from IntelliJ's keymap.
     * Returns null if no shortcut is defined.
     */
    fun getShortcutText(actionId: String): String? {
        return try {
            val keymapManager = KeymapManager.getInstance()
            val activeKeymap = keymapManager.activeKeymap
            
            val shortcuts = activeKeymap.getShortcuts(actionId)
            if (shortcuts.isEmpty()) return null
            
            // Get the first shortcut and extract key stroke from it
            val shortcut = shortcuts.first()
            // Use reflection to get the key stroke since API varies
            val keyStrokeField = shortcut.javaClass.getMethod("getKeyStroke")
            @Suppress("UNCHECKED_CAST")
            val keyStroke = keyStrokeField.invoke(shortcut) as? javax.swing.KeyStroke
            keyStroke?.let { keyStrokeToString(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts a KeyStroke to a human-readable string.
     */
    private fun keyStrokeToString(keyStroke: KeyStroke): String {
        val modifiers = keyStroke.modifiers
        val keyCode = keyStroke.keyCode
        
        val parts = mutableListOf<String>()
        
        // Modifier keys
        if (modifiers and java.awt.event.InputEvent.META_DOWN_MASK != 0) parts.add("\u2318")  // Cmd
        if (modifiers and java.awt.event.InputEvent.CTRL_DOWN_MASK != 0) parts.add("Ctrl")
        if (modifiers and java.awt.event.InputEvent.ALT_DOWN_MASK != 0) parts.add("\u2325")  // Option
        if (modifiers and java.awt.event.InputEvent.SHIFT_DOWN_MASK != 0) parts.add("\u21E7")  // Shift
        
        // Key
        val keyText = when (keyCode) {
            KeyEvent.VK_BACK_SPACE -> "\u232B"
            KeyEvent.VK_DELETE -> "\u2326"
            KeyEvent.VK_ENTER -> "\u21B5"
            KeyEvent.VK_ESCAPE -> "\u238B"
            KeyEvent.VK_TAB -> "\u21E5"
            KeyEvent.VK_SPACE -> "Space"
            KeyEvent.VK_UP -> "\u2191"
            KeyEvent.VK_DOWN -> "\u2193"
            KeyEvent.VK_LEFT -> "\u2190"
            KeyEvent.VK_RIGHT -> "\u2192"
            else -> {
                val char = KeyEvent.getKeyText(keyCode)
                if (char.length == 1) char else KeyEvent.getKeyText(keyCode)
            }
        }
        parts.add(keyText)
        
        return parts.joinToString("")
    }

    /**
     * Creates a presentation with the action name and its keyboard shortcut hint.
     */
    fun createPresentationWithHint(actionName: String, actionId: String): Presentation {
        val presentation = Presentation(actionName)
        
        val shortcutText = getShortcutText(actionId)
        if (shortcutText != null) {
            presentation.setText("$actionName ($shortcutText)")
        }
        
        return presentation
    }

    /**
     * Creates an AnAction that wraps another action and displays its shortcut hint.
     */
    fun wrapWithHint(
        action: AnAction,
        presentation: Presentation,
        actionId: String
    ): AnAction {
        val shortcutText = getShortcutText(actionId)
        
        val newText = if (shortcutText != null) {
            "${presentation.text} ($shortcutText)"
        } else {
            presentation.text ?: ""
        }
        
        return object : AnAction(newText) {
            override fun actionPerformed(e: AnActionEvent) {
                action.actionPerformed(e)
            }
            
            override fun update(e: AnActionEvent) {
                action.update(e)
            }
        }
    }

    /**
     * Gets all registered chord shortcuts for display.
     */
    fun getChordShortcuts(): List<Pair<String, String>> {
        val registry = ChordRegistry.getInstance()
        val shortcuts = mutableListOf<Pair<String, String>>()
        
        for (action in registry.getAllActions()) {
            val keyCode = action.secondKeyCode
            val baseKey = if (action.baseKeyCode == KeyEvent.VK_BACK_QUOTE) "`" else KeyEvent.getKeyText(action.baseKeyCode)
            val secondKey = if (keyCode in KeyEvent.VK_A..KeyEvent.VK_Z) {
                String(charArrayOf('a' + (keyCode - KeyEvent.VK_A)))
            } else {
                KeyEvent.getKeyText(keyCode)
            }
            
            val display = "$baseKey$secondKey"
            shortcuts.add(display to action.displayName)
        }
        
        return shortcuts.distinct().sortedBy { it.second }
    }

    /**
     * Creates an HTML-formatted reference of all shortcuts for display in the reference panel.
     */
    fun createShortcutReferenceHtml(context: PanelContext): String {
        val registry = ChordRegistry.getInstance()
        val actions = registry.getActionsForContext(context)
        
        val sb = StringBuilder()
        sb.append("<html><body>")
        sb.append("<h3>${context.name.replace("_", " ")} Shortcuts</h3>")
        sb.append("<table border='0' cellpadding='2'>")
        
        // Group by category
        val byCategory = actions.groupBy { it.category }
        for (category in byCategory.keys.sortedBy { it.ordinal }) {
            sb.append("<tr><td colspan='2'><b>${category.name.replace("_", " ")}</b></td></tr>")
            
            for (action in byCategory[category]!!.sortedBy { it.displayName }) {
                val chord = if (action.baseKeyCode == KeyEvent.VK_BACK_QUOTE) {
                    "`" + getSecondKeyChar(action.secondKeyCode)
                } else {
                    "?"
                }
                
                sb.append("<tr>")
                sb.append("<td><code>$chord</code></td>")
                sb.append("<td>${action.displayName}</td>")
                sb.append("</tr>")
            }
            sb.append("<tr><td colspan='2'>&nbsp;</td></tr>")
        }
        
        sb.append("</table>")
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun getSecondKeyChar(keyCode: Int): String {
        return if (keyCode in KeyEvent.VK_A..KeyEvent.VK_Z) {
            String(charArrayOf('a' + (keyCode - KeyEvent.VK_A)))
        } else {
            KeyEvent.getKeyText(keyCode)
        }
    }
}

/**
 * Extension function to add shortcut hint to an action.
 */
fun AnAction.withShortcutHint(text: String, actionId: String): AnAction {
    val shortcutText = ShortcutHintHelper.getShortcutText(actionId)
    val newText = if (shortcutText != null) "$text ($shortcutText)" else text
    
    return object : AnAction(newText) {
        override fun actionPerformed(e: AnActionEvent) {
            this@withShortcutHint.actionPerformed(e)
        }
        
        override fun update(e: AnActionEvent) {
            this@withShortcutHint.update(e)
        }
    }
}
