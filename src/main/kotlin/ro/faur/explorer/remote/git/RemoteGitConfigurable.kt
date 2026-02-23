package ro.faur.explorer.remote.git

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings page: Tools > System Explorer > Remote > Git.
 */
class RemoteGitConfigurable : Configurable {

    private var panel: JPanel? = null

    private val enabledCheckBox = JBCheckBox("Enable git integration", true)
    private val statusRefreshSpinner = JSpinner(SpinnerNumberModel(30, 1, 3600, 1))
    private val commandTimeoutSpinner = JSpinner(SpinnerNumberModel(15, 1, 300, 1))
    private val showGitColorsCheckBox = JBCheckBox("Show git colors on tree", true)
    private val showBranchInStatusBarCheckBox = JBCheckBox("Show branch in status bar", true)

    override fun getDisplayName(): String = "Git"

    override fun createComponent(): JComponent {
        val settings = RemoteGitSettings.getInstance().state
        loadFromState(settings)

        val p = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
        }

        var row = 0

        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            p.add(JBLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            p.add(component, gbc)
            row++
        }

        fun addCheckRow(component: JBCheckBox) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0
            gbc.gridwidth = 2
            p.add(component, gbc)
            gbc.gridwidth = 1
            row++
        }

        addCheckRow(enabledCheckBox)
        addRow("Status refresh interval (s):", statusRefreshSpinner)
        addRow("Command timeout (s):", commandTimeoutSpinner)
        addCheckRow(showGitColorsCheckBox)
        addCheckRow(showBranchInStatusBarCheckBox)

        // Filler to push everything to the top
        gbc.gridx = 0; gbc.gridy = row; gbc.weighty = 1.0
        gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH
        p.add(JPanel(), gbc)

        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val s = RemoteGitSettings.getInstance().state
        return enabledCheckBox.isSelected != s.enabled
            || statusRefreshSpinner.value as Int != s.statusRefreshIntervalSec
            || commandTimeoutSpinner.value as Int != s.commandTimeoutSec
            || showGitColorsCheckBox.isSelected != s.showGitColorsOnTree
            || showBranchInStatusBarCheckBox.isSelected != s.showBranchInStatusBar
    }

    override fun apply() {
        val s = RemoteGitSettings.getInstance().state
        s.enabled = enabledCheckBox.isSelected
        s.statusRefreshIntervalSec = statusRefreshSpinner.value as Int
        s.commandTimeoutSec = commandTimeoutSpinner.value as Int
        s.showGitColorsOnTree = showGitColorsCheckBox.isSelected
        s.showBranchInStatusBar = showBranchInStatusBarCheckBox.isSelected
    }

    override fun reset() {
        loadFromState(RemoteGitSettings.getInstance().state)
    }

    private fun loadFromState(s: RemoteGitSettings.State) {
        enabledCheckBox.isSelected = s.enabled
        statusRefreshSpinner.value = s.statusRefreshIntervalSec
        commandTimeoutSpinner.value = s.commandTimeoutSec
        showGitColorsCheckBox.isSelected = s.showGitColorsOnTree
        showBranchInStatusBarCheckBox.isSelected = s.showBranchInStatusBar
    }
}
