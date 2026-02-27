package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import ro.faur.explorer.actions.NavigationActions
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Abstract base for all browser panels (local filesystem and remote SFTP).
 *
 * Owns: path bar, filter field, navigation history, status bar, and Hidden/Permissions
 * icon-toggle buttons.
 *
 * Subclasses implement the data-source-specific contract and call [assemblePanelUI]
 * from their init block to compose the full layout.
 */
abstract class BrowserPanel : JPanel(BorderLayout()), Disposable {

    // ── Panel identity (displayed in BrowserHost tab strip) ────────────────
    abstract val panelLabel: String
    abstract val panelIcon: Icon?

    // ── Path bar + filter ──────────────────────────────────────────────────
    protected val pathField   = JBTextField()
    protected val filterField = JBTextField().apply { emptyText.text = "Filter (e.g. *.kt)" }

    // ── Status bar ─────────────────────────────────────────────────────────
    protected val statusLabel = JBLabel("Ready")

    // ── Toggle buttons (replaces checkboxes) ──────────────────────────────
    val hiddenToggle      = iconToggle(AllIcons.Actions.Show, "Show hidden files")
    val permissionsToggle: JButton? = if (!SystemInfo.isWindows) iconToggle(AllIcons.Nodes.SecurityRole, "Show permissions") else null

    var showHidden: Boolean      = false ; protected set
    var showPermissions: Boolean = false ; protected set

    // ── Navigation history ─────────────────────────────────────────────────
    val history = NavigationActions.NavigationHistory()

    /** Called by ExplorerPanel whenever nav state may have changed (after navigate, panel switch). */
    var onNavStateChanged: (() -> Unit)? = null

    // ── Focus tracking (set by BrowserHost on tab switch) ──────────────────
    var isFocused: Boolean = false

    // ── Listener references for cleanup ───────────────────────────────────
    private lateinit var pathListener: ActionListener

    // ── Abstract contract ──────────────────────────────────────────────────

    /** Perform the actual I/O navigation to [path]. Called after history is updated. */
    abstract fun doNavigateTo(path: String)

    /** Navigate one level up from the current path. */
    abstract fun navigateUp()

    /** Reload the current directory. */
    abstract fun refresh()

    /** Returns the path currently displayed in this panel. */
    abstract fun currentPath(): String

    /** Returns absolute paths of all currently selected items. */
    abstract fun getSelectedPaths(): List<String>

    /** Navigate to the "home" directory for this panel type. */
    abstract fun navigateHome()

    // ── Concrete navigation (history managed centrally here) ───────────────

    open fun navigateTo(path: String) {
        history.push(path)
        doNavigateTo(path)
        pathField.text = path
        notifyNavStateChanged()
    }

    fun navigateBack(): Boolean {
        val previous = history.back() ?: return false
        doNavigateTo(previous)
        pathField.text = previous
        notifyNavStateChanged()
        return true
    }

    fun navigateForward(): Boolean {
        val next = history.forward() ?: return false
        doNavigateTo(next)
        pathField.text = next
        notifyNavStateChanged()
        return true
    }

    fun canGoBack(): Boolean    = history.canGoBack
    fun canGoForward(): Boolean = history.canGoForward

    protected fun notifyNavStateChanged() {
        onNavStateChanged?.invoke()
    }

    // ── UI construction helpers ─────────────────────────────────────────────

    /**
     * Builds the NORTH section: pathField, then filterField.
     * Subclasses may override to add extra controls (e.g. preset combobox).
     */
    protected open fun buildSharedNorth(): JPanel {
        val north = JPanel()
        north.layout = BoxLayout(north, BoxLayout.Y_AXIS)

        pathField.alignmentX  = Component.LEFT_ALIGNMENT
        pathField.maximumSize = Dimension(Int.MAX_VALUE, pathField.preferredSize.height)
        north.add(pathField)

        filterField.alignmentX  = Component.LEFT_ALIGNMENT
        filterField.maximumSize = Dimension(Int.MAX_VALUE, filterField.preferredSize.height)
        north.add(filterField)

        return north
    }

    /**
     * Builds the SOUTH status bar: status label on the left, toggle buttons on the right.
     */
    protected fun buildStatusBar(): JPanel {
        val bar = JPanel(BorderLayout())
        bar.add(statusLabel, BorderLayout.LINE_START)

        val togglePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply { isOpaque = false }
        togglePanel.add(hiddenToggle)
        permissionsToggle?.let { togglePanel.add(it) }
        bar.add(togglePanel, BorderLayout.LINE_END)

        return bar
    }

    /**
     * Composes the full panel: north toolbar + [contentArea] + south status bar.
     * Subclasses MUST call this from their init block.
     */
    protected fun assemblePanelUI(contentArea: Component) {
        add(buildSharedNorth(), BorderLayout.NORTH)
        add(contentArea,        BorderLayout.CENTER)
        add(buildStatusBar(),   BorderLayout.SOUTH)
    }

    /**
     * Wires the path field listener. Subclasses call this from init after [assemblePanelUI].
     */
    protected fun wireSharedListeners() {
        pathListener = ActionListener {
            val typed = pathField.text.trim()
            if (typed.isNotEmpty()) onPathEntered(typed)
        }
        pathField.addActionListener(pathListener)
        notifyNavStateChanged()
    }

    /**
     * Called when the user presses Enter in the path field.
     * Subclasses override to validate and navigate (e.g., check VFS for local, attempt SFTP for remote).
     */
    protected open fun onPathEntered(path: String) {
        navigateTo(path)
    }

    /**
     * Wires the Hidden and Permissions toggle button listeners.
     * [onHiddenChanged] is called on EDT when the user toggles hidden files.
     * [onPermissionsChanged] is called on EDT when the user toggles permissions (null = no-op).
     */
    protected fun wireToggleListeners(
        onHiddenChanged: (Boolean) -> Unit,
        onPermissionsChanged: ((Boolean) -> Unit)? = null,
    ) {
        hiddenToggle.addActionListener {
            showHidden = !showHidden
            refreshToggleAppearance()
            onHiddenChanged(showHidden)
        }
        permissionsToggle?.addActionListener {
            showPermissions = !showPermissions
            refreshToggleAppearance()
            onPermissionsChanged?.invoke(showPermissions)
        }
    }

    protected fun refreshToggleAppearance() {
        hiddenToggle.isContentAreaFilled = showHidden
        hiddenToggle.background = if (showHidden) JBUI.CurrentTheme.ActionButton.pressedBackground() else null
        permissionsToggle?.isContentAreaFilled = showPermissions
        permissionsToggle?.background = if (showPermissions) JBUI.CurrentTheme.ActionButton.pressedBackground() else null
    }

    // ── Base dispose (removes shared listeners) ────────────────────────────

    override fun dispose() {
        if (::pathListener.isInitialized) pathField.removeActionListener(pathListener)
    }
}

private fun iconToggle(icon: Icon, tooltip: String): JButton = JButton(icon).apply {
    isBorderPainted     = false
    isContentAreaFilled = false
    isFocusPainted      = false
    isRolloverEnabled   = true
    preferredSize       = JBUI.size(22, 22)
    margin              = JBUI.emptyInsets()
    toolTipText         = tooltip
}
