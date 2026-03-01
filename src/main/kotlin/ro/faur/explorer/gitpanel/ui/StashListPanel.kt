package ro.faur.explorer.gitpanel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import ro.faur.explorer.gitpanel.StashEntry
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

class StashListPanel : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<StashEntry>()
    val list = JBList(listModel)

    /** Called with the selected StashEntry when the toolbar/menu Apply action is triggered. */
    var onApply: ((StashEntry) -> Unit)? = null

    /** Called with the selected StashEntry when the toolbar/menu Pop action is triggered. */
    var onPop: ((StashEntry) -> Unit)? = null

    /** Called with the selected StashEntry when the toolbar/menu Drop action is triggered. */
    var onDrop: ((StashEntry) -> Unit)? = null

    /** Called whenever the list selection changes. Null when nothing is selected. */
    var onStashSelected: ((StashEntry?) -> Unit)? = null

    init {
        list.cellRenderer = object : ColoredListCellRenderer<StashEntry>() {
            override fun customizeCellRenderer(
                list: JList<out StashEntry>,
                value: StashEntry,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                append(value.message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        list.emptyText.text = "No stashes"

        list.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            onStashSelected?.invoke(list.selectedValue)
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                list.selectedIndex = idx
                buildContextMenu().show(list, e.x, e.y)
            }
        })

        val applyAction = object : AnAction("Apply", "Apply stash (keep in list)", AllIcons.Actions.Download) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                val entry = list.selectedValue ?: return
                onApply?.invoke(entry)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = list.selectedValue != null
            }
        }

        val popAction = object : AnAction("Pop", "Apply stash and remove from list", AllIcons.Vcs.Merge) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                val entry = list.selectedValue ?: return
                onPop?.invoke(entry)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = list.selectedValue != null
            }
        }

        val dropAction = object : AnAction("Drop", "Delete stash without applying", AllIcons.General.Remove) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                val entry = list.selectedValue ?: return
                onDrop?.invoke(entry)
            }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = list.selectedValue != null
            }
        }

        val group = DefaultActionGroup().apply {
            add(applyAction)
            add(popAction)
            add(dropAction)
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("StashPanel.Toolbar", group, true)
        toolbar.targetComponent = list

        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setEntries(entries: List<StashEntry>) {
        listModel.clear()
        entries.forEach { listModel.addElement(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildContextMenu(): JPopupMenu {
        val menu = JPopupMenu()

        val applyItem = JMenuItem("Apply").apply {
            addActionListener {
                val entry = list.selectedValue ?: return@addActionListener
                onApply?.invoke(entry)
            }
        }

        val popItem = JMenuItem("Pop").apply {
            addActionListener {
                val entry = list.selectedValue ?: return@addActionListener
                onPop?.invoke(entry)
            }
        }

        val dropItem = JMenuItem("Drop").apply {
            addActionListener {
                val entry = list.selectedValue ?: return@addActionListener
                onDrop?.invoke(entry)
            }
        }

        menu.add(applyItem)
        menu.add(popItem)
        menu.add(dropItem)

        return menu
    }
}
