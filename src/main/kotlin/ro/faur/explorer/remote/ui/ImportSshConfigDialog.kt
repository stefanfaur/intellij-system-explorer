package ro.faur.explorer.remote.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.SshConfigEntry
import ro.faur.explorer.remote.SshConfigParser
import ro.faur.explorer.remote.settings.RemoteConnectionSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Dialog for selectively importing SSH hosts from ~/.ssh/config.
 * Read-only — never writes to the SSH config file.
 */
class ImportSshConfigDialog(
    private val project: Project,
) : DialogWrapper(project) {

    private val entries: List<SshConfigEntry>
    private val existingNames: Set<String>

    private val tableModel = object : DefaultTableModel(COLUMNS, 0) {
        override fun isCellEditable(row: Int, col: Int) = col == COL_CHECK
        override fun getColumnClass(col: Int) =
            if (col == COL_CHECK) java.lang.Boolean::class.java else String::class.java
    }
    private val table = JBTable(tableModel)

    init {
        title = "Import from ~/.ssh/config"
        val configPath = Paths.get(System.getProperty("user.home"), ".ssh", "config")
        entries = SshConfigParser.parse(configPath)
        existingNames = RemoteConnectionSettings.getInstance(project)
            .state.connections.map { it.name }.toSet()
        init()
        populateTable()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        val headerText = when {
            entries.isEmpty() -> "No hosts found in ~/.ssh/config."
            else -> "Found ${entries.size} host${if (entries.size == 1) "" else "s"} in ~/.ssh/config. " +
                    "Select which ones to import."
        }
        panel.add(JBLabel(headerText), BorderLayout.NORTH)

        configureTableColumns()
        val scrollPane = JBScrollPane(table)

        if (entries.isEmpty()) {
            panel.preferredSize = Dimension(560, 160)
            panel.add(scrollPane, BorderLayout.CENTER)
            return panel
        }

        val btnPanel = JPanel()
        val selectAll = JButton("Select All")
        val deselectAll = JButton("Deselect All")
        selectAll.addActionListener { setAllChecked(true) }
        deselectAll.addActionListener { setAllChecked(false) }
        btnPanel.add(selectAll)
        btnPanel.add(deselectAll)

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(btnPanel, BorderLayout.SOUTH)
        panel.preferredSize = Dimension(620, 360)
        return panel
    }

    fun getOKButtonText() = "Import Selected"

    /**
     * Returns a [ConnectionProfile] for every checked row.
     * Call after [showAndGet] returns true.
     */
    fun getSelectedProfiles(): List<ConnectionProfile> {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        return (0 until tableModel.rowCount)
            .filter { tableModel.getValueAt(it, COL_CHECK) as Boolean }
            .map { entries[it].toConnectionProfile() }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun populateTable() {
        tableModel.rowCount = 0
        for (entry in entries) {
            val alreadyExists = entry.alias in existingNames
            tableModel.addRow(arrayOf(
                !alreadyExists,
                entry.alias,
                "${entry.hostName}:${entry.port}",
                entry.username ?: "",
                if (entry.identityFile != null) "Key file" else "Agent",
                if (alreadyExists) "Already imported" else "",
            ))
        }
    }

    private fun configureTableColumns() {
        table.columnModel.getColumn(COL_CHECK).apply { maxWidth = 30; minWidth = 30; preferredWidth = 30 }
        table.columnModel.getColumn(COL_HOST).preferredWidth = 140
        table.columnModel.getColumn(COL_HOSTPORT).preferredWidth = 140
        table.columnModel.getColumn(COL_USER).preferredWidth = 100
        table.columnModel.getColumn(COL_AUTH).preferredWidth = 70
        table.columnModel.getColumn(COL_STATUS).preferredWidth = 110
    }

    private fun setAllChecked(checked: Boolean) {
        for (row in 0 until tableModel.rowCount) tableModel.setValueAt(checked, row, COL_CHECK)
    }

    companion object {
        private val COLUMNS = arrayOf("", "Host", "HostName:Port", "User", "Auth", "Status")
        private const val COL_CHECK = 0
        private const val COL_HOST = 1
        private const val COL_HOSTPORT = 2
        private const val COL_USER = 3
        private const val COL_AUTH = 4
        private const val COL_STATUS = 5
    }
}

// ── Conversion ────────────────────────────────────────────────────────────────

private fun SshConfigEntry.toConnectionProfile(): ConnectionProfile {
    val resolvedKey = identityFile?.let { path ->
        if (path.startsWith("~/")) System.getProperty("user.home") + path.substring(1) else path
    }
    return ConnectionProfile(
        name = alias,
        host = hostName,
        port = port,
        username = username ?: System.getProperty("user.name") ?: "",
        authMethod = if (resolvedKey != null) ConnectionProfile.AuthMethod.KEY_FILE
                     else ConnectionProfile.AuthMethod.AGENT,
        keyFilePath = resolvedKey,
        proxyJump = proxyJump,
    )
}
