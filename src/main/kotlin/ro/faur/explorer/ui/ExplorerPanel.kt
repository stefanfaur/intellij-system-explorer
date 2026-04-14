package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.gitpanel.ActiveBrowserTracker
import ro.faur.explorer.remote.ConnectionProfile
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ui.RemoteBrowserPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Thin outer shell for the System Explorer tool window.
 *
 * Owns: a [BrowserHost] (CENTER), a unified icon-only toolbar row (NORTH) with
 * nav buttons and action buttons, and the tab strip (SOUTH).
 * All browser logic lives in [BrowserHost] and the [BrowserPanel] subclasses it manages.
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

    // ── Icon buttons ────────────────────────────────────────────────────────
    private val backBtn     = iconButton(AllIcons.Actions.Back,           "Back (Alt+Left)")
    private val forwardBtn  = iconButton(AllIcons.Actions.Forward,        "Forward (Alt+Right)")
    private val upBtn       = iconButton(AllIcons.Actions.MoveUp,         "Up (Alt+Left)")
    private val homeBtn     = iconButton(AllIcons.Nodes.HomeFolder,       "Home")
    private val refreshBtn  = iconButton(AllIcons.Actions.Refresh,        "Refresh (F5)")
    private val connectBtn  = iconButton(AllIcons.Webreferences.Server,   "Connect to SSH\u2026")
    private val settingsBtn = iconButton(AllIcons.General.Settings,       "Settings")

    val component: JComponent

    init {
        Disposer.register(this, browserHost)

        // Wire nav buttons to active panel
        backBtn.addActionListener    { browserHost.activePanel.navigateBack() }
        forwardBtn.addActionListener { browserHost.activePanel.navigateForward() }
        upBtn.addActionListener      { browserHost.activePanel.navigateUp() }
        homeBtn.addActionListener    { browserHost.activePanel.navigateHome() }
        refreshBtn.addActionListener { browserHost.activePanel.refresh() }

        // Wire action buttons
        settingsBtn.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "System Explorer")
        }
        connectBtn.addActionListener { showConnectDropdown(connectBtn) }

        // Keep nav state fresh when panel switches; also sync git panel to new active panel
        browserHost.onActivePanelChanged = { panel ->
            bindNavCallbackToPanel(panel)
            refreshNavButtons()
            val connName = (panel as? RemoteBrowserPanel)?.getConnectionName()
            ActiveBrowserTracker.getInstance(project).reportNavigation(connName, panel.currentPath())
        }

        // Bind callback to the initial local panel
        bindNavCallbackToPanel(browserHost.localPanel)
        refreshNavButtons()

        // ── Unified toolbar row ──────────────────────────────────────────────
        val navGroup = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
            add(backBtn); add(forwardBtn); add(upBtn); add(homeBtn); add(refreshBtn)
        }
        val actionGroup = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
            add(connectBtn); add(settingsBtn)
        }
        val toolbarLeft = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(navGroup); add(ToolbarSeparator()); add(actionGroup)
        }
        val toolbarRow = JPanel(BorderLayout()).apply {
            isOpaque   = true
            background = JBUI.CurrentTheme.ToolWindow.headerBackground(true)
            border     = JBUI.Borders.empty(1, 2)
            add(toolbarLeft, BorderLayout.WEST)
        }

        // ── Root assembly ────────────────────────────────────────────────────
        val root = JPanel(BorderLayout())
        root.add(toolbarRow,                BorderLayout.NORTH)
        root.add(browserHost,               BorderLayout.CENTER)
        root.add(browserHost.tabScrollPane, BorderLayout.SOUTH)

        component = root
        (root as JComponent).putClientProperty(ExplorerPanel::class.java.name, this)
    }

    // ── Nav state helpers ──────────────────────────────────────────────────

    private fun refreshNavButtons() {
        val p = browserHost.activePanel
        backBtn.isEnabled    = p.canGoBack()
        forwardBtn.isEnabled = p.canGoForward()
    }

    private fun bindNavCallbackToPanel(panel: BrowserPanel) {
        panel.onNavStateChanged = { refreshNavButtons() }
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

        val manageItem = javax.swing.JMenuItem("Manage Connections...").apply {
            icon = AllIcons.General.Settings
            addActionListener { ro.faur.explorer.remote.ui.ManageConnectionsDialog(project).show() }
        }
        menu.add(manageItem)

        menu.show(anchor, 0, anchor.height)
    }

    // ── Connect / disconnect ───────────────────────────────────────────────

    fun connectToRemote(profile: ConnectionProfile, preloadedPassword: String? = null) {
        connectBtn.isEnabled   = false
        connectBtn.toolTipText = "Connecting to ${profile.name}…"

        fun onSuccess(ops: ro.faur.explorer.remote.SftpFileOperations, gitManager: SftpConnectionManager?) {
            val panel = RemoteBrowserPanel(project, connectionManager = gitManager)
            panel.connect(profile.name, ops, profile)
            bindNavCallbackToPanel(panel)
            browserHost.addPanel(panel)
            connectBtn.isEnabled   = true
            connectBtn.toolTipText = null
        }

        fun onError(e: Exception) {
            connectBtn.isEnabled   = true
            connectBtn.toolTipText = null
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("SftpBrowser.Notifications")
                .createNotification("Failed to connect to ${profile.name}",
                    e.message ?: "Unknown error", com.intellij.notification.NotificationType.ERROR)
                .notify(project)
        }

        fun onCancelled() { connectBtn.isEnabled = true; connectBtn.toolTipText = null }

        fun connectGitManager(password: String?, keyPassphrase: String?): SftpConnectionManager? {
            return try { SftpConnectionManager().also { it.connect(profile, password, keyPassphrase) } }
            catch (_: Exception) { null }
        }

        // Phase 1: Resolve password — may need to show a dialog on EDT
        fun doConnect(password: String?, keyPassphrase: String?) {
            AppExecutorUtil.getAppExecutorService().execute {
                try {
                    val ops = ro.faur.explorer.remote.SftpFileOperations.create(profile, password, keyPassphrase)
                    val gm  = connectGitManager(password, keyPassphrase)
                    ApplicationManager.getApplication().invokeLater { onSuccess(ops, gm) }
                } catch (e: Exception) {
                    if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE && keyPassphrase == null) {
                        // Need key passphrase — prompt on EDT, then retry
                        ApplicationManager.getApplication().invokeLater {
                            val dialog = ro.faur.explorer.remote.ui.PasswordPromptDialog(project, "'${profile.name}' key file")
                            if (dialog.showAndGet()) {
                                val kp = String(dialog.getPassword())
                                val shouldRememberKp = dialog.rememberPassword.isSelected
                                if (shouldRememberKp)
                                    ro.faur.explorer.remote.security.CredentialHandler.storeKeyPassphrase(profile.name, kp)
                                doConnect(password, kp)
                            } else onCancelled()
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater { onError(e) }
                    }
                }
            }
        }

        when (profile.authMethod) {
            ConnectionProfile.AuthMethod.PASSWORD -> {
                val resolved = preloadedPassword
                    ?: ro.faur.explorer.remote.security.CredentialHandler.getPassword(profile.name)
                if (resolved != null) {
                    doConnect(resolved, null)
                } else {
                    // Need password — prompt on EDT, then connect in background
                    val dialog = ro.faur.explorer.remote.ui.PasswordPromptDialog(project, profile.name)
                    if (dialog.showAndGet()) {
                        val pw = String(dialog.getPassword())
                        val shouldRememberPassword = dialog.rememberPassword.isSelected
                        if (shouldRememberPassword)
                            ro.faur.explorer.remote.security.CredentialHandler
                                .storePassword(profile.name, profile.username, pw)
                        doConnect(pw, null)
                    } else onCancelled()
                }
            }
            else -> {
                val storedKP = if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE)
                    ro.faur.explorer.remote.security.CredentialHandler.getKeyPassphrase(profile.name) else null
                doConnect(null, storedKP)
            }
        }
    }

    // ── Backward-compat stubs ──────────────────────────────────────────────
    fun getRemoteTreePanel() = null
    val isRemotePanelVisible: Boolean get() = browserHost.panelCount > 1
    val isActiveRemote: Boolean get() = browserHost.activePanel is RemoteBrowserPanel

    /** Returns the name of the first active remote connection, or null if none. */
    fun getActiveConnectionName(): String? =
        browserHost.getPanels().drop(1)
            .filterIsInstance<RemoteBrowserPanel>()
            .mapNotNull { it.getConnectionName() }
            .firstOrNull()

    /** Disconnects all remote panels and removes them from BrowserHost. */
    fun disconnectRemote() {
        val toRemove = browserHost.getPanels()
            .drop(1)  // keep panel 0 (local)
            .filterIsInstance<RemoteBrowserPanel>()
        for (panel in toRemove) {
            panel.disconnect()
            browserHost.removePanel(panel)
        }
    }

    override fun dispose() {
        // browserHost is registered as child disposable via Disposer.register above
    }
}

private fun iconButton(icon: Icon, tooltip: String): JButton = JButton(icon).apply {
    isBorderPainted     = false
    isContentAreaFilled = false
    isFocusPainted      = false
    isRolloverEnabled   = true
    preferredSize       = JBUI.size(24, 24)
    margin              = JBUI.emptyInsets()
    toolTipText         = tooltip
}

private class ToolbarSeparator : JComponent() {
    init {
        preferredSize = JBUI.size(8, 16)
        maximumSize   = Dimension(JBUI.scale(8), JBUI.scale(16))
        isOpaque      = false
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        val x = width / 2
        g2.drawLine(x, 2, x, height - 2)
        g2.dispose()
    }
}
