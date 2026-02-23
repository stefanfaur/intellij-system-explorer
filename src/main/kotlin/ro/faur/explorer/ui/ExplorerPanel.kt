package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ui.RemoteBrowserPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Thin outer shell for the System Explorer tool window.
 *
 * Owns only: a [BrowserHost] (CENTER) and a small toolbar (NORTH) with
 * Settings and Connect buttons. All browser logic lives in [BrowserHost]
 * and the [BrowserPanel] subclasses it manages.
 */
class ExplorerPanel(private val project: Project) : Disposable {

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ExplorerPanel::class.java)
    }

    val browserHost = BrowserHost(LocalBrowserPanel(project))

    // ── Backward-compat shims (used by existing IDE actions) ───────────────
    internal val fileTreeComponent  get() = browserHost.localPanel.fileTreeComponent
    val currentPath: String          get() = browserHost.activePanel.currentPath()
    fun getNavigationHistory(): NavigationActions.NavigationHistory = browserHost.localPanel.history
    fun navigateTo(path: String)     = browserHost.activePanel.navigateTo(path)
    fun goBack(): Boolean            = browserHost.activePanel.navigateBack()
    fun canGoBack(): Boolean         = browserHost.activePanel.canGoBack()
    fun focusFileTree()              = browserHost.localPanel.focusFileTree()
    fun toggleHiddenFiles()          = browserHost.localPanel.toggleHiddenFiles()
    fun getStatusText(): String      = browserHost.localPanel.getStatusText()
    internal fun updateStatus()      = browserHost.localPanel.updateStatus()

    // ── Outer toolbar ──────────────────────────────────────────────────────
    private val settingsButton = JButton(AllIcons.General.Settings).apply { toolTipText = "Settings" }
    private val connectButton  = JButton("Connect ▾", AllIcons.Nodes.DataTables)

    val component: JComponent

    init {
        Disposer.register(this, browserHost)

        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "System Explorer")
        }
        connectButton.addActionListener { showConnectDropdown(connectButton) }

        val outerToolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 2)).apply {
            add(connectButton)
            add(settingsButton)
        }

        val root = JPanel(BorderLayout())
        root.add(outerToolbar, BorderLayout.NORTH)
        root.add(browserHost,  BorderLayout.CENTER)

        component = root
        (root as javax.swing.JComponent).putClientProperty(ExplorerPanel::class.java.name, this)
    }

    // ── Connect dropdown ───────────────────────────────────────────────────

    private fun showConnectDropdown(anchor: JButton) {
        val menu = javax.swing.JPopupMenu()

        val profiles = ro.faur.explorer.remote.settings.RemoteConnectionSettings
            .getInstance(project).state.connections
        if (profiles.isEmpty()) {
            val emptyItem = javax.swing.JMenuItem("No saved connections").apply { isEnabled = false }
            menu.add(emptyItem)
        } else {
            profiles.forEach { profile ->
                val item = javax.swing.JMenuItem(
                    "<html><b>${profile.name}</b>&nbsp;&nbsp;" +
                    "<font color='gray'>${profile.username}@${profile.host}</font></html>"
                ).apply {
                    icon = AllIcons.Nodes.DataTables
                    addActionListener { connectToRemote(profile) }
                }
                menu.add(item)
            }
        }
        menu.addSeparator()

        val newItem = javax.swing.JMenuItem("+ New Connection...").apply {
            addActionListener {
                val dialog = ro.faur.explorer.remote.ui.ConnectionDialog(project)
                if (dialog.showAndGet()) {
                    val profile  = dialog.getProfile()
                    val password = dialog.getPassword()?.let { String(it) }
                    if (dialog.shouldRememberPassword() && password != null)
                        ro.faur.explorer.remote.security.CredentialHandler
                            .storePassword(profile.name, profile.username, password)
                    ro.faur.explorer.remote.settings.RemoteConnectionSettings.getInstance(project).addConnection(profile)
                }
            }
        }
        menu.add(newItem)

        val manageItem = javax.swing.JMenuItem("🔧 Manage Connections...").apply {
            addActionListener { ro.faur.explorer.remote.ui.ManageConnectionsDialog(project).show() }
        }
        menu.add(manageItem)

        menu.show(anchor, 0, anchor.height)
    }

    // ── Connect / disconnect ───────────────────────────────────────────────

    fun connectToRemote(profile: ConnectionProfile, preloadedPassword: String? = null) {
        connectButton.isEnabled   = false
        connectButton.toolTipText = "Connecting to ${profile.name}…"

        fun onSuccess(ops: ro.faur.explorer.remote.SftpFileOperations, gitManager: SftpConnectionManager?) {
            val panel = RemoteBrowserPanel(project, connectionManager = gitManager)
            panel.connect(profile.name, ops, profile)
            browserHost.addPanel(panel)
            connectButton.isEnabled   = true
            connectButton.toolTipText = null
        }

        fun onError(e: Exception) {
            connectButton.isEnabled   = true
            connectButton.toolTipText = null
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("SftpBrowser.Notifications")
                .createNotification("Failed to connect to ${profile.name}",
                    e.message ?: "Unknown error", com.intellij.notification.NotificationType.ERROR)
                .notify(project)
        }

        fun onCancelled() { connectButton.isEnabled = true; connectButton.toolTipText = null }

        fun connectGitManager(password: String?, keyPassphrase: String?): SftpConnectionManager? {
            return try { SftpConnectionManager().also { it.connect(profile, password, keyPassphrase) } }
            catch (_: Exception) { null }
        }

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.submit {
            val password: String? = when (profile.authMethod) {
                ConnectionProfile.AuthMethod.PASSWORD -> {
                    var resolved = preloadedPassword
                        ?: ro.faur.explorer.remote.security.CredentialHandler.getPassword(profile.name)
                    if (resolved == null) {
                        var cancelled = false
                        javax.swing.SwingUtilities.invokeAndWait {
                            val dialog = ro.faur.explorer.remote.ui.PasswordPromptDialog(project, profile.name)
                            if (dialog.showAndGet()) {
                                resolved = dialog.getPassword()
                                if (dialog.rememberPassword.isSelected)
                                    ro.faur.explorer.remote.security.CredentialHandler
                                        .storePassword(profile.name, profile.username, resolved!!)
                            } else cancelled = true
                        }
                        if (cancelled) { javax.swing.SwingUtilities.invokeLater { onCancelled() }; return@submit }
                    }
                    resolved
                }
                else -> null
            }
            val storedKP = if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE)
                ro.faur.explorer.remote.security.CredentialHandler.getKeyPassphrase(profile.name) else null

            try {
                val ops = ro.faur.explorer.remote.SftpFileOperations.create(profile, password, storedKP)
                val gm  = connectGitManager(password, storedKP)
                javax.swing.SwingUtilities.invokeLater { onSuccess(ops, gm) }
            } catch (e: Exception) {
                if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE && storedKP == null) {
                    var kp: String? = null; var cancelled = false
                    javax.swing.SwingUtilities.invokeAndWait {
                        val dialog = ro.faur.explorer.remote.ui.PasswordPromptDialog(project, "'${profile.name}' key file")
                        if (dialog.showAndGet()) {
                            kp = dialog.getPassword()
                            if (dialog.rememberPassword.isSelected)
                                ro.faur.explorer.remote.security.CredentialHandler.storeKeyPassphrase(profile.name, kp!!)
                        } else cancelled = true
                    }
                    if (cancelled || kp == null) javax.swing.SwingUtilities.invokeLater { onCancelled() }
                    else try {
                        val ops = ro.faur.explorer.remote.SftpFileOperations.create(profile, password, kp)
                        val gm  = connectGitManager(password, kp)
                        javax.swing.SwingUtilities.invokeLater { onSuccess(ops, gm) }
                    } catch (e2: Exception) { javax.swing.SwingUtilities.invokeLater { onError(e2) } }
                } else javax.swing.SwingUtilities.invokeLater { onError(e) }
            } finally { executor.shutdown() }
        }
    }

    // ── Backward-compat stubs ──────────────────────────────────────────────
    fun getRemoteTreePanel() = null
    val isRemotePanelVisible: Boolean get() = browserHost.panelCount > 1

    /** Returns the name of the first active remote connection, or null if none. */
    fun getActiveConnectionName(): String? =
        browserHost.getPanels().drop(1)
            .filterIsInstance<RemoteBrowserPanel>()
            .mapNotNull { it.getConnectionName() }
            .firstOrNull()

    /** Disconnects all remote panels and removes them from BrowserHost. */
    fun disconnectRemote() {
        val toRemove = (browserHost.panelCount - 1 downTo 1).toList()
        for (i in toRemove) {
            (browserHost.getPanels().getOrNull(i) as? RemoteBrowserPanel)?.disconnect()
            browserHost.removePanel(i)
        }
    }

    override fun dispose() {
        // browserHost is registered as child disposable via Disposer.register above
    }
}
