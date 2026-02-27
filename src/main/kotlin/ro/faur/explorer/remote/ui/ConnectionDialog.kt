package ro.faur.explorer.remote.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.SftpFileOperations
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.Executors
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JRadioButton
import javax.swing.JSeparator
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Dialog for creating/editing SSH connection profiles.
 * Supports SSH agent, key file, and password authentication methods.
 * Fields are dynamically enabled/disabled based on the selected auth method.
 */
class ConnectionDialog(
    private val project: Project,
    private val existingProfile: ConnectionProfile? = null
) : DialogWrapper(project) {

    private val nameField = JTextField(existingProfile?.name ?: "", 20)
    private val hostField = JTextField(existingProfile?.host ?: "", 20)
    private val portField = JTextField((existingProfile?.port ?: 22).toString(), 5)
    private val usernameField = JTextField(existingProfile?.username ?: "", 20)

    private val authGroup = ButtonGroup()
    private val agentRadio = JRadioButton(
        "SSH Agent (recommended)",
        existingProfile?.authMethod == ConnectionProfile.AuthMethod.AGENT || existingProfile == null
    )
    private val keyRadio = JRadioButton(
        "Key file",
        existingProfile?.authMethod == ConnectionProfile.AuthMethod.KEY_FILE
    )
    private val passwordRadio = JRadioButton(
        "Password",
        existingProfile?.authMethod == ConnectionProfile.AuthMethod.PASSWORD
    )

    private val keyFileBrowser = TextFieldWithBrowseButton().apply {
        text = existingProfile?.keyFilePath ?: "~/.ssh/id_ed25519"
        addBrowseFolderListener(
            "Select Private Key File",
            "Choose the SSH private key file for authentication",
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
    }

    private val passwordField = JPasswordField(20)
    private val rememberPasswordCheck = JCheckBox("Remember password")

    private val testStatusLabel = JLabel("").apply { isVisible = false }
    @Volatile private var testExecutor: java.util.concurrent.ExecutorService? = null

    init {
        title = if (existingProfile != null) "Edit SSH Connection" else "New SSH Connection"
        authGroup.add(agentRadio)
        authGroup.add(keyRadio)
        authGroup.add(passwordRadio)

        // Wire radio buttons to enable/disable fields
        agentRadio.addActionListener { updateFieldStates() }
        keyRadio.addActionListener { updateFieldStates() }
        passwordRadio.addActionListener { updateFieldStates() }

        init()
        updateFieldStates()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        var row = 0

        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE
            gbc.gridwidth = 1; gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.gridwidth = 2; gbc.weightx = 1.0
            panel.add(component, gbc)
            gbc.weightx = 0.0
            row++
        }

        fun addFullRow(component: JComponent) {
            gbc.gridx = 0; gbc.gridy = row
            gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL
            panel.add(component, gbc)
            gbc.gridwidth = 1
            row++
        }

        addRow("Name:", nameField)
        addRow("Host:", hostField)
        addRow("Port:", portField)
        addRow("Username:", usernameField)

        // Auth label
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("Authentication:"), gbc)
        gbc.gridwidth = 1
        row++

        addFullRow(agentRadio)
        addFullRow(keyRadio)

        // Key file row (label + browser inline, indented)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("  Key file:"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(keyFileBrowser, gbc)
        gbc.weightx = 0.0
        row++

        addFullRow(passwordRadio)

        // Password row (label + field inline, indented)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("  Password:"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(passwordField, gbc)
        gbc.weightx = 0.0
        row++

        // Remember password checkbox (indented under password)
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE
        panel.add(rememberPasswordRow(), gbc)
        row++

        // Separator before test area
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(), gbc)
        row++

        // Test Connection button + inline status label
        val testButton = JButton("Test Connection")
        testButton.addActionListener { testConnection() }
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(testButton, gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(testStatusLabel, gbc)
        gbc.weightx = 0.0
        row++

        panel.preferredSize = Dimension(480, 430)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("Name is required", nameField)
        if (hostField.text.isBlank()) return ValidationInfo("Host is required", hostField)
        val portVal = portField.text.toIntOrNull()
        if (portVal == null || portVal !in 1..65535) return ValidationInfo("Port must be between 1 and 65535", portField)
        if (usernameField.text.isBlank()) return ValidationInfo("Username is required", usernameField)
        if (keyRadio.isSelected && keyFileBrowser.text.isBlank()) {
            return ValidationInfo("Key file path is required", keyFileBrowser.textField)
        }
        if (passwordRadio.isSelected && passwordField.password.isEmpty()) {
            return ValidationInfo("Password is required", passwordField)
        }
        return null
    }

    fun getProfile(): ConnectionProfile {
        val authMethod = when {
            keyRadio.isSelected -> ConnectionProfile.AuthMethod.KEY_FILE
            passwordRadio.isSelected -> ConnectionProfile.AuthMethod.PASSWORD
            else -> ConnectionProfile.AuthMethod.AGENT
        }
        return ConnectionProfile(
            name = nameField.text.trim(),
            host = hostField.text.trim(),
            port = portField.text.trim().toIntOrNull() ?: 22,
            username = usernameField.text.trim(),
            authMethod = authMethod,
            keyFilePath = if (keyRadio.isSelected) keyFileBrowser.text.trim() else null,
        )
    }

    /**
     * Returns the entered password as a CharArray, or null if password auth is not selected.
     * The caller is responsible for clearing the array after use.
     */
    fun getPassword(): CharArray? {
        return if (passwordRadio.isSelected) passwordField.password else null
    }

    /**
     * Returns true if the "Remember password" checkbox is checked.
     */
    fun shouldRememberPassword(): Boolean = rememberPasswordCheck.isSelected

    override fun doCancelAction() {
        testExecutor?.shutdownNow()
        super.doCancelAction()
    }

    private fun rememberPasswordRow(): JPanel {
        val hint = JLabel("?").apply {
            toolTipText = "Stored securely via IntelliJ's credential store (OS keychain on macOS/Windows, KWallet/GNOME Keyring on Linux). Never saved as plain text."
            foreground = Color(128, 128, 128)
            font = font.deriveFont(font.size2D - 1f)
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(rememberPasswordCheck)
            add(hint)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun updateFieldStates() {
        val isAgent = agentRadio.isSelected
        val isKey = keyRadio.isSelected
        val isPassword = passwordRadio.isSelected

        keyFileBrowser.isEnabled = isKey
        keyFileBrowser.textField.isEnabled = isKey
        passwordField.isEnabled = isPassword
        rememberPasswordCheck.isEnabled = isPassword
        rememberPasswordCheck.isVisible = isPassword
    }

    private fun testConnection() {
        val host = hostField.text.trim()
        val username = usernameField.text.trim()
        val port = portField.text.trim().toIntOrNull() ?: 22

        if (host.isBlank()) { showTestStatus("Host is required", Color.RED); return }
        if (username.isBlank()) { showTestStatus("Username is required", Color.RED); return }
        if (keyRadio.isSelected && keyFileBrowser.text.isBlank()) {
            showTestStatus("Key file path is required", Color.RED); return
        }
        if (passwordRadio.isSelected && passwordField.password.isEmpty()) {
            showTestStatus("Password is required", Color.RED); return
        }

        val authMethod = when {
            keyRadio.isSelected -> ConnectionProfile.AuthMethod.KEY_FILE
            passwordRadio.isSelected -> ConnectionProfile.AuthMethod.PASSWORD
            else -> ConnectionProfile.AuthMethod.AGENT
        }
        val password = if (passwordRadio.isSelected) String(passwordField.password) else null
        val profile = ConnectionProfile(
            name = nameField.text.trim().ifBlank { "_test_" },
            host = host,
            port = port,
            username = username,
            authMethod = authMethod,
            keyFilePath = if (keyRadio.isSelected) keyFileBrowser.text.trim() else null,
        )

        showTestStatus("Testing…", null)
        testExecutor?.shutdownNow()
        val executor = Executors.newSingleThreadExecutor()
        testExecutor = executor
        executor.submit {
            try {
                val ops = SftpFileOperations.create(profile, password, keyPassphrase = null)
                ops.close()
                SwingUtilities.invokeLater { showTestStatus("✓ Connected successfully", Color(0, 130, 0)) }
            } catch (e: Exception) {
                if (authMethod == ConnectionProfile.AuthMethod.KEY_FILE) {
                    var enteredPassphrase: String? = null
                    var cancelled = false
                    SwingUtilities.invokeAndWait {
                        val dialog = PasswordPromptDialog(project, "'${keyFileBrowser.text.trim()}' key file")
                        if (dialog.showAndGet()) {
                            enteredPassphrase = String(dialog.getPassword())
                        } else {
                            cancelled = true
                        }
                    }
                    if (cancelled || enteredPassphrase == null) {
                        SwingUtilities.invokeLater { showTestStatus("Cancelled", null) }
                    } else {
                        try {
                            val ops = SftpFileOperations.create(profile, password, enteredPassphrase)
                            ops.close()
                            SwingUtilities.invokeLater { showTestStatus("✓ Connected successfully", Color(0, 130, 0)) }
                        } catch (e2: Exception) {
                            SwingUtilities.invokeLater { showTestStatus("✗ ${e2.message}", Color.RED) }
                        }
                    }
                } else {
                    SwingUtilities.invokeLater { showTestStatus("✗ ${e.message}", Color.RED) }
                }
            } finally {
                executor.shutdown()
            }
        }
    }

    private fun showTestStatus(text: String, color: Color?) {
        testStatusLabel.text = text
        testStatusLabel.foreground = color ?: testStatusLabel.parent?.foreground ?: Color.BLACK
        testStatusLabel.isVisible = true
    }
}
