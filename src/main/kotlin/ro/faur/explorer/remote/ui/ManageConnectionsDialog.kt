package ro.faur.explorer.remote.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.SftpFileOperations
import ro.faur.explorer.remote.security.CredentialHandler
import ro.faur.explorer.remote.settings.RemoteConnectionSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import java.util.concurrent.Executors

class ManageConnectionsDialog(private val project: Project) : DialogWrapper(project) {

    private val columnNames = arrayOf("Name", "Host:Port", "Auth")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, col: Int) = false
    }
    private val table = JBTable(tableModel)
    private val statusLabel = JBLabel("").apply { isVisible = false }

    private fun settings() = RemoteConnectionSettings.getInstance(project)

    init {
        title = "Manage Connections"
        isModal = false   // non-modal so user can see the explorer while managing
        init()
        refreshTable()
    }

    override fun createCenterPanel(): JComponent {
        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction { addConnection() }
            .setEditAction { editConnection() }
            .setRemoveAction { removeConnection() }
            .addExtraAction(object : AnActionButton("Test Connection", null,
                com.intellij.icons.AllIcons.Actions.Execute) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    testConnection()
                }
                override fun isEnabled() = table.selectedRow >= 0
            })
            .addExtraAction(object : AnActionButton("Import from ~/.ssh/config", null,
                com.intellij.icons.AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    importFromSshConfig()
                }
                override fun isEnabled() = true
            })

        val panel = JPanel(BorderLayout())
        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)
        panel.preferredSize = Dimension(560, 300)
        return panel
    }

    override fun createActions() = arrayOf(myOKAction.apply { putValue(javax.swing.Action.NAME, "Close") })

    private fun refreshTable() {
        tableModel.rowCount = 0
        settings().state.connections.forEach { p ->
            tableModel.addRow(arrayOf(
                p.name,
                "${p.host}:${p.port}",
                p.authMethod.name.lowercase().replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
            ))
        }
    }

    private fun addConnection() {
        val dialog = ConnectionDialog(project)
        if (!dialog.showAndGet()) return
        val profile = dialog.getProfile()
        val password = dialog.getPassword()?.let { String(it) }
        if (dialog.shouldRememberPassword() && password != null) {
            CredentialHandler.storePassword(profile.name, profile.username, password)
        }
        if (!settings().addConnection(profile)) {
            setStatus("A connection named '${profile.name}' already exists.", Color.RED)
            return
        }
        refreshTable()
        clearStatus()
    }

    private fun editConnection() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val name = tableModel.getValueAt(row, 0) as String
        val existing = settings().getConnection(name) ?: return
        val dialog = ConnectionDialog(project, existing)
        if (!dialog.showAndGet()) return
        val updated = dialog.getProfile()
        val password = dialog.getPassword()?.let { String(it) }
        if (dialog.shouldRememberPassword() && password != null) {
            CredentialHandler.storePassword(updated.name, updated.username, password)
        }
        settings().removeConnection(name)
        settings().addConnection(updated)
        refreshTable()
        clearStatus()
    }

    private fun removeConnection() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val name = tableModel.getValueAt(row, 0) as String
        settings().removeConnection(name)
        CredentialHandler.removePassword(name)
        CredentialHandler.removeKeyPassphrase(name)
        refreshTable()
        clearStatus()
    }

    private fun testConnection() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val name = tableModel.getValueAt(row, 0) as String
        val profile = settings().getConnection(name) ?: return

        setStatus("Testing…", null)

        val password = if (profile.authMethod == ConnectionProfile.AuthMethod.PASSWORD)
            CredentialHandler.getPassword(profile.name) else null
        val storedKeyPassphrase = if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE)
            CredentialHandler.getKeyPassphrase(profile.name) else null

        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            try {
                val ops = SftpFileOperations.create(profile, password, storedKeyPassphrase)
                ops.close()
                SwingUtilities.invokeLater { setStatus("✓ Connected successfully", Color(0, 130, 0)) }
            } catch (e: Exception) {
                if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE && storedKeyPassphrase == null) {
                    var enteredPassphrase: String? = null
                    var remember = false
                    var cancelled = false
                    SwingUtilities.invokeAndWait {
                        val dialog = PasswordPromptDialog(project, "'$name' key file")
                        if (dialog.showAndGet()) {
                            enteredPassphrase = dialog.getPassword()
                            remember = dialog.rememberPassword.isSelected
                        } else {
                            cancelled = true
                        }
                    }
                    if (cancelled || enteredPassphrase == null) {
                        SwingUtilities.invokeLater { clearStatus() }
                    } else {
                        if (remember) CredentialHandler.storeKeyPassphrase(name, enteredPassphrase!!)
                        try {
                            val ops = SftpFileOperations.create(profile, password, enteredPassphrase)
                            ops.close()
                            SwingUtilities.invokeLater { setStatus("✓ Connected successfully", Color(0, 130, 0)) }
                        } catch (e2: Exception) {
                            SwingUtilities.invokeLater { setStatus("✗ ${e2.message}", Color.RED) }
                        }
                    }
                } else {
                    SwingUtilities.invokeLater { setStatus("✗ ${e.message}", Color.RED) }
                }
            } finally {
                executor.shutdown()
            }
        }
    }

    private fun importFromSshConfig() {
        val dialog = ImportSshConfigDialog(project)
        if (!dialog.showAndGet()) return
        val profiles = dialog.getSelectedProfiles()
        if (profiles.isEmpty()) {
            setStatus("No connections selected.", null)
            return
        }
        var imported = 0
        var skipped = 0
        for (profile in profiles) {
            if (settings().addConnection(profile)) imported++ else skipped++
        }
        refreshTable()
        val msg = buildString {
            append("Imported $imported connection${if (imported == 1) "" else "s"}.")
            if (skipped > 0) append(" Skipped $skipped duplicate${if (skipped == 1) "" else "s"}.")
        }
        setStatus(msg, if (skipped > 0) Color(180, 120, 0) else Color(0, 130, 0))
    }

    private fun setStatus(text: String, color: Color?) {
        statusLabel.text = text
        statusLabel.foreground = color ?: statusLabel.parent?.foreground ?: Color.BLACK
        statusLabel.isVisible = true
    }

    private fun clearStatus() {
        statusLabel.isVisible = false
    }
}
