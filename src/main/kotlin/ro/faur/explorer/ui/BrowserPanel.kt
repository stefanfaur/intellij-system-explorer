package ro.faur.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import ro.faur.explorer.actions.NavigationActions
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Abstract base for all browser panels (local filesystem and remote SFTP).
 *
 * Owns: shared toolbar (Back/Forward/Up/Home/Refresh), path bar, filter field,
 * navigation history, status bar, and Hidden/Permissions toggle checkboxes.
 *
 * Subclasses implement the data-source-specific contract and call [assemblePanelUI]
 * from their init block to compose the full layout.
 */
abstract class BrowserPanel : JPanel(BorderLayout()), Disposable {

    // ── Panel identity (displayed in BrowserHost tab strip) ────────────────
    abstract val panelLabel: String
    abstract val panelIcon: Icon?

    // ── Shared toolbar buttons ─────────────────────────────────────────────
    protected val backButton    = JButton("<")
    protected val forwardButton = JButton(">")
    protected val upButton      = JButton("Up", AllIcons.Actions.MoveUp)
    protected val homeButton    = JButton("Home", AllIcons.Nodes.HomeFolder)
    protected val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)

    // ── Path bar + filter ──────────────────────────────────────────────────
    protected val pathField   = JBTextField()
    protected val filterField = JBTextField().apply { emptyText.text = "Filter (e.g. *.kt)" }

    // ── Status bar ─────────────────────────────────────────────────────────
    protected val statusLabel = JBLabel("Ready")

    // ── Toggle checkboxes (each panel owns its own state) ──────────────────
    val hiddenCheckbox      = JBCheckBox("Hidden")
    val permissionsCheckbox: JBCheckBox? = if (!SystemInfo.isWindows) JBCheckBox("Permissions") else null

    var showHidden: Boolean      = false ; protected set
    var showPermissions: Boolean = false ; protected set

    // ── Navigation history ─────────────────────────────────────────────────
    val history = NavigationActions.NavigationHistory()

    // ── Focus tracking (set by BrowserHost on tab switch) ──────────────────
    var isFocused: Boolean = false

    // ── Listener references for cleanup ───────────────────────────────────
    private lateinit var backListener:    ActionListener
    private lateinit var forwardListener: ActionListener
    private lateinit var upListener:      ActionListener
    private lateinit var homeListener:    ActionListener
    private lateinit var refreshListener: ActionListener
    private lateinit var pathListener:    ActionListener
    private var hiddenListener:      ItemListener? = null
    private var permissionsListener: ItemListener? = null

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

    // ── Concrete navigation (history managed centrally here) ───────────────

    fun navigateTo(path: String) {
        history.push(path)
        doNavigateTo(path)
        pathField.text = path
        updateHistoryButtons()
    }

    fun navigateBack(): Boolean {
        val previous = history.back() ?: return false
        doNavigateTo(previous)
        pathField.text = previous
        updateHistoryButtons()
        return true
    }

    fun navigateForward(): Boolean {
        val next = history.forward() ?: return false
        doNavigateTo(next)
        pathField.text = next
        updateHistoryButtons()
        return true
    }

    fun canGoBack(): Boolean    = history.canGoBack
    fun canGoForward(): Boolean = history.canGoForward

    protected fun updateHistoryButtons() {
        backButton.isEnabled    = history.canGoBack
        forwardButton.isEnabled = history.canGoForward
    }

    // ── UI construction helpers ─────────────────────────────────────────────

    /**
     * Builds the NORTH section: one row of nav buttons, then pathField, then filterField.
     */
    protected fun buildSharedNorth(): JPanel {
        val north = JPanel()
        north.layout = BoxLayout(north, BoxLayout.Y_AXIS)

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        buttonRow.add(backButton)
        buttonRow.add(forwardButton)
        buttonRow.add(upButton)
        buttonRow.add(homeButton)
        buttonRow.add(refreshButton)
        buttonRow.alignmentX   = Component.LEFT_ALIGNMENT
        buttonRow.maximumSize  = Dimension(Int.MAX_VALUE, buttonRow.preferredSize.height)
        north.add(buttonRow)

        pathField.alignmentX  = Component.LEFT_ALIGNMENT
        pathField.maximumSize = Dimension(Int.MAX_VALUE, pathField.preferredSize.height)
        north.add(pathField)

        filterField.alignmentX  = Component.LEFT_ALIGNMENT
        filterField.maximumSize = Dimension(Int.MAX_VALUE, filterField.preferredSize.height)
        north.add(filterField)

        return north
    }

    /**
     * Builds the SOUTH status bar: status label on the left, checkboxes on the right.
     */
    protected fun buildStatusBar(): JPanel {
        val bar = JPanel(BorderLayout())
        bar.add(statusLabel, BorderLayout.LINE_START)

        val checkboxPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        checkboxPanel.add(hiddenCheckbox)
        permissionsCheckbox?.let { checkboxPanel.add(it) }
        bar.add(checkboxPanel, BorderLayout.LINE_END)

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
     * Wires toolbar button listeners. Subclasses call this from init after [assemblePanelUI].
     *
     * [onUp]   — called when Up button is clicked; subclass provides path-specific logic.
     * [onHome] — called when Home button is clicked.
     */
    protected fun wireSharedToolbarListeners(onUp: () -> Unit, onHome: () -> Unit) {
        backListener    = ActionListener { navigateBack() }
        forwardListener = ActionListener { navigateForward() }
        upListener      = ActionListener { onUp() }
        homeListener    = ActionListener { onHome() }
        refreshListener = ActionListener { refresh() }

        backButton.addActionListener(backListener)
        forwardButton.addActionListener(forwardListener)
        upButton.addActionListener(upListener)
        homeButton.addActionListener(homeListener)
        refreshButton.addActionListener(refreshListener)

        pathListener = ActionListener {
            val typed = pathField.text.trim()
            if (typed.isNotEmpty()) onPathEntered(typed)
        }
        pathField.addActionListener(pathListener)

        updateHistoryButtons()
    }

    /**
     * Called when the user presses Enter in the path field.
     * Subclasses override to validate and navigate (e.g., check VFS for local, attempt SFTP for remote).
     */
    protected open fun onPathEntered(path: String) {
        navigateTo(path)
    }

    /**
     * Wires the Hidden and Permissions checkbox item listeners.
     * [onHiddenChanged] is called on EDT when the user toggles hidden files.
     * [onPermissionsChanged] is called on EDT when the user toggles permissions (null = no-op).
     */
    protected fun wireToggleListeners(
        onHiddenChanged: (Boolean) -> Unit,
        onPermissionsChanged: ((Boolean) -> Unit)? = null,
    ) {
        hiddenListener = ItemListener { e ->
            showHidden = e.stateChange == ItemEvent.SELECTED
            onHiddenChanged(showHidden)
        }
        hiddenCheckbox.addItemListener(hiddenListener!!)

        permissionsCheckbox?.let { cb ->
            permissionsListener = ItemListener { e ->
                showPermissions = e.stateChange == ItemEvent.SELECTED
                onPermissionsChanged?.invoke(showPermissions)
            }
            cb.addItemListener(permissionsListener!!)
        }
    }

    // ── Base dispose (removes shared listeners) ────────────────────────────

    override fun dispose() {
        if (::backListener.isInitialized)    backButton.removeActionListener(backListener)
        if (::forwardListener.isInitialized) forwardButton.removeActionListener(forwardListener)
        if (::upListener.isInitialized)      upButton.removeActionListener(upListener)
        if (::homeListener.isInitialized)    homeButton.removeActionListener(homeListener)
        if (::refreshListener.isInitialized) refreshButton.removeActionListener(refreshListener)
        if (::pathListener.isInitialized)    pathField.removeActionListener(pathListener)
        hiddenListener?.let      { hiddenCheckbox.removeItemListener(it) }
        permissionsListener?.let { permissionsCheckbox?.removeItemListener(it) }
    }
}
