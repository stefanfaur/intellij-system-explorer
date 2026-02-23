package ro.faur.explorer.remote.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField

/**
 * Minimal dialog prompting for a password when connecting to a server that
 * uses PASSWORD auth but has no stored credential in PasswordSafe.
 */
class PasswordPromptDialog(
    project: Project,
    private val connectionName: String,
) : DialogWrapper(project) {

    private val passwordField = JPasswordField(20)
    val rememberPassword = JBCheckBox("Remember password")

    init {
        title = "Password Required"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        panel.add(JBLabel("Enter passphrase for $connectionName:"), gbc)

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 1.0
        panel.add(passwordField, gbc)

        gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 0.0
        panel.add(rememberPasswordRow(), gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (passwordField.password.isEmpty()) {
            return ValidationInfo("Password is required", passwordField)
        }
        return null
    }

    private fun rememberPasswordRow(): JPanel {
        val hint = JLabel(" ?").apply {
            toolTipText = "Stored securely via IntelliJ's credential store (OS keychain on macOS/Windows, " +
                          "KWallet/GNOME Keyring on Linux). Never saved as plain text."
            foreground  = JBUI.CurrentTheme.Label.disabledForeground()
            font        = font.deriveFont(Font.BOLD, font.size2D - 1f)
            border      = JBUI.Borders.empty(0, 4)
            cursor      = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(rememberPassword)
            add(hint)
        }
    }

    fun getPassword(): String = String(passwordField.password)

    override fun getPreferredFocusedComponent() = passwordField
}
