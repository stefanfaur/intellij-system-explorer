package ro.faur.explorer.remote.settings

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
 * Settings page: Tools > System Explorer > Remote.
 * Parent for Remote Git settings.
 */
class RemoteExplorerConfigurable : Configurable {

    private var panel: JPanel? = null

    private val keepaliveSpinner = JSpinner(SpinnerNumberModel(60, 1, 3600, 1))
    private val connectionTimeoutSpinner = JSpinner(SpinnerNumberModel(10, 1, 300, 1))
    private val cacheTtlSpinner = JSpinner(SpinnerNumberModel(30, 0, 3600, 1))
    private val maxFileSizeSpinner = JSpinner(SpinnerNumberModel(10, 1, 500, 1))
    private val maxTransferSizeSpinner = JSpinner(SpinnerNumberModel(100L, 1L, 10000L, 10L))
    private val tailInitialLinesSpinner = JSpinner(SpinnerNumberModel(1000, 1, 100000, 100))
    private val showSshConfigHostsCheckBox = JBCheckBox("Show SSH config hosts", true)
    private val secureDeleteCheckBox = JBCheckBox("Secure delete sensitive files", true)
    private val keepaliveMaxFailuresSpinner = JSpinner(SpinnerNumberModel(3, 1, 20, 1))
    private val connectionRetryAttemptsSpinner = JSpinner(SpinnerNumberModel(2, 0, 10, 1))
    private val maxCachedDirectoriesSpinner = JSpinner(SpinnerNumberModel(200, 10, 2000, 10))

    override fun getDisplayName(): String = "Remote SSH"

    override fun createComponent(): JComponent {
        val settings = RemoteExplorerSettings.getInstance().state
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

        addRow("SSH keepalive interval (s):", keepaliveSpinner)
        addRow("Keepalive max failures:", keepaliveMaxFailuresSpinner)
        addRow("Connection timeout (s):", connectionTimeoutSpinner)
        addRow("Connection retry attempts:", connectionRetryAttemptsSpinner)
        addRow("Directory cache TTL (s):", cacheTtlSpinner)
        addRow("Max cached directories:", maxCachedDirectoriesSpinner)
        addRow("Max file size for editor (MB):", maxFileSizeSpinner)
        addRow("Max transfer size (MB):", maxTransferSizeSpinner)
        addRow("Tail log initial lines:", tailInitialLinesSpinner)
        addCheckRow(showSshConfigHostsCheckBox)
        addCheckRow(secureDeleteCheckBox)

        // Filler to push everything to the top
        gbc.gridx = 0; gbc.gridy = row; gbc.weighty = 1.0
        gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH
        p.add(JPanel(), gbc)

        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val s = RemoteExplorerSettings.getInstance().state
        return keepaliveSpinner.value as Int != s.keepaliveIntervalSec
            || keepaliveMaxFailuresSpinner.value as Int != s.keepaliveMaxFailures
            || connectionTimeoutSpinner.value as Int != s.connectionTimeoutSec
            || connectionRetryAttemptsSpinner.value as Int != s.connectionRetryAttempts
            || cacheTtlSpinner.value as Int != s.directoryCacheTtlSec
            || maxCachedDirectoriesSpinner.value as Int != s.maxCachedDirectories
            || maxFileSizeSpinner.value as Int != s.maxFileSizeMb
            || maxTransferSizeSpinner.value as Long != s.maxTransferSizeMb
            || tailInitialLinesSpinner.value as Int != s.tailInitialLines
            || showSshConfigHostsCheckBox.isSelected != s.showSshConfigHosts
            || secureDeleteCheckBox.isSelected != s.secureDeleteSensitiveFiles
    }

    override fun apply() {
        val s = RemoteExplorerSettings.getInstance().state
        s.keepaliveIntervalSec = keepaliveSpinner.value as Int
        s.keepaliveMaxFailures = keepaliveMaxFailuresSpinner.value as Int
        s.connectionTimeoutSec = connectionTimeoutSpinner.value as Int
        s.connectionRetryAttempts = connectionRetryAttemptsSpinner.value as Int
        s.directoryCacheTtlSec = cacheTtlSpinner.value as Int
        s.maxCachedDirectories = maxCachedDirectoriesSpinner.value as Int
        s.maxFileSizeMb = maxFileSizeSpinner.value as Int
        s.maxTransferSizeMb = maxTransferSizeSpinner.value as Long
        s.tailInitialLines = tailInitialLinesSpinner.value as Int
        s.showSshConfigHosts = showSshConfigHostsCheckBox.isSelected
        s.secureDeleteSensitiveFiles = secureDeleteCheckBox.isSelected
    }

    override fun reset() {
        loadFromState(RemoteExplorerSettings.getInstance().state)
    }

    private fun loadFromState(s: RemoteExplorerSettings.State) {
        keepaliveSpinner.value = s.keepaliveIntervalSec
        keepaliveMaxFailuresSpinner.value = s.keepaliveMaxFailures
        connectionTimeoutSpinner.value = s.connectionTimeoutSec
        connectionRetryAttemptsSpinner.value = s.connectionRetryAttempts
        cacheTtlSpinner.value = s.directoryCacheTtlSec
        maxCachedDirectoriesSpinner.value = s.maxCachedDirectories
        maxFileSizeSpinner.value = s.maxFileSizeMb
        maxTransferSizeSpinner.value = s.maxTransferSizeMb
        tailInitialLinesSpinner.value = s.tailInitialLines
        showSshConfigHostsCheckBox.isSelected = s.showSshConfigHosts
        secureDeleteCheckBox.isSelected = s.secureDeleteSensitiveFiles
    }
}
