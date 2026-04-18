package ro.faur.explorer.shortcuts.settings

import com.intellij.openapi.options.Configurable
import ro.faur.explorer.shortcuts.ShortcutSettings
import javax.swing.JComponent

/**
 * Configurable wrapper for the shortcut settings panel.
 * 
 * Implements IntelliJ's Configurable interface to integrate the
 * ShortcutSettingsPanel into the IDE's settings dialog.
 */
class ShortcutSettingsConfigurable : Configurable {
    
    private var panel: ShortcutSettingsPanel? = null

    override fun getDisplayName(): String = "Keyboard Shortcuts"

    override fun getHelpTopic(): String? = "reference.dialogs.keyboard.shortcuts"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = ShortcutSettingsPanel()
        }
        return panel!!
    }

    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
