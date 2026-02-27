package ro.faur.explorer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Thin parent node for all System Explorer settings sub-pages.
 * Displays plugin name only; all settings live in sub-pages.
 */
class SystemExplorerConfigurable : Configurable {

    override fun getDisplayName(): String = "System Explorer"

    override fun createComponent(): JComponent = panel {
        row {
            label("System Explorer — configure sub-pages below.")
                .comment("Local Browser · Quick Open · Remote SSH · Remote Git")
        }
    }

    override fun isModified(): Boolean = false
    override fun apply() {}
    override fun reset() {}
}
