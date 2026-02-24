package ro.faur.explorer.gitpanel.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import ro.faur.explorer.gitpanel.CommitFile
import ro.faur.explorer.remote.git.GitFileStatus
import ro.faur.explorer.remote.git.RemoteGitTreeDecorator
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ChangedFilesPanel : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<CommitFile>()
    private val list = JBList(listModel)
    private val header = JLabel("Changed Files")

    private var stagingMode = false
    // Tracks checked state by file path. Rebuilt on every setFiles() call.
    private val checkedPaths = mutableSetOf<String>()

    // ── Header row with Select All / None toggle ──────────────────────────────

    private val selectAllLabel = JLabel("☑ All").apply {
        val label = this
        border = javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 0)
        cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
        font = font.deriveFont(java.awt.Font.PLAIN, 11f)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val allPaths = stagingFiles().map { it.path }
                if (checkedPaths.containsAll(allPaths)) {
                    checkedPaths.clear()
                    label.text = "☐ All"
                } else {
                    checkedPaths.addAll(allPaths)
                    label.text = "☑ All"
                }
                list.repaint()
            }
        })
        isVisible = false
    }

    init {
        // Toggle checkbox when clicking anywhere on the row
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!stagingMode) return
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                val file = listModel.getElementAt(idx)
                if (file.status == GitFileStatus.UNMERGED || file.status == GitFileStatus.IGNORED) return
                if (checkedPaths.contains(file.path)) checkedPaths.remove(file.path)
                else checkedPaths.add(file.path)
                list.repaint()
            }
        })

        list.cellRenderer = DualModeRenderer()
        list.emptyText.text = "No changes"

        val headerRow = JPanel(BorderLayout())
        headerRow.border = javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6)
        headerRow.add(header, BorderLayout.WEST)
        headerRow.add(selectAllLabel, BorderLayout.EAST)
        headerRow.isOpaque = false

        add(headerRow, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setMode(staging: Boolean) {
        stagingMode = staging
        selectAllLabel.isVisible = staging
    }

    fun setFiles(files: List<CommitFile>, headerText: String) {
        header.text = headerText
        checkedPaths.clear()
        listModel.clear()
        files.forEach { listModel.addElement(it) }
        if (stagingMode) {
            // UNMERGED and IGNORED files cannot be staged — exclude from defaults
            files.filter { it.status != GitFileStatus.UNMERGED && it.status != GitFileStatus.IGNORED }
                 .forEach { checkedPaths.add(it.path) }
        }
        list.emptyText.text = "No changes"
        list.repaint()
    }

    /** Returns checked file paths. Only meaningful in staging mode. */
    fun getCheckedPaths(): List<String> {
        if (!stagingMode) return emptyList()
        return checkedPaths.toList()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stagingFiles(): List<CommitFile> =
        (0 until listModel.size).map { listModel.getElementAt(it) }
            .filter { it.status != GitFileStatus.UNMERGED && it.status != GitFileStatus.IGNORED }

    private fun statusLetter(status: GitFileStatus): String = when (status) {
        GitFileStatus.MODIFIED  -> "M"
        GitFileStatus.ADDED     -> "A"
        GitFileStatus.DELETED   -> "D"
        GitFileStatus.RENAMED   -> "R"
        GitFileStatus.COPIED    -> "C"
        GitFileStatus.UNTRACKED -> "?"
        GitFileStatus.IGNORED   -> "!"
        GitFileStatus.UNMERGED  -> "U"
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private inner class DualModeRenderer : ListCellRenderer<CommitFile> {

        // Staging mode: checkbox row
        private val checkBox = JCheckBox().apply { isOpaque = true }
        private val checkLabel = JLabel()
        private val checkRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = true
            add(checkBox)
            add(checkLabel)
        }

        // History mode: reuse the existing colored renderer logic inline
        private val coloredRenderer = object : ColoredListCellRenderer<CommitFile>() {
            override fun customizeCellRenderer(
                list: JList<out CommitFile>, value: CommitFile,
                index: Int, selected: Boolean, hasFocus: Boolean
            ) {
                val color: Color? = RemoteGitTreeDecorator.colorFor(value.status)
                val statusLetter = statusLetter(value.status)
                val textAttr = if (color != null)
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)
                else SimpleTextAttributes.REGULAR_ATTRIBUTES
                val boldAttr = if (color != null)
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color)
                else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                append("$statusLetter  ", boldAttr)
                append(value.path, textAttr)
                if (value.oldPath != null)
                    append("  \u2190 ${value.oldPath}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

        override fun getListCellRendererComponent(
            list: JList<out CommitFile>, value: CommitFile?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            if (value == null) return JLabel()

            if (!stagingMode) {
                return coloredRenderer.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                )
            }

            // Staging mode
            val isUnstageble = value.status == GitFileStatus.UNMERGED || value.status == GitFileStatus.IGNORED
            checkBox.isSelected = checkedPaths.contains(value.path)
            checkBox.isEnabled = !isUnstageble
            checkBox.background = if (isSelected) list.selectionBackground else list.background

            val color: Color? = if (isUnstageble) list.foreground.let {
                java.awt.Color(it.red, it.green, it.blue, 100)
            } else RemoteGitTreeDecorator.colorFor(value.status)

            val text = buildString {
                append(statusLetter(value.status))
                append("  ")
                append(value.path)
                if (value.oldPath != null) append("  \u2190 ${value.oldPath}")
                if (value.status == GitFileStatus.UNMERGED) append("  [resolve conflicts first]")
                if (value.status == GitFileStatus.IGNORED) append("  [ignored]")
            }
            checkLabel.text = text
            checkLabel.foreground = color ?: list.foreground
            checkLabel.isEnabled = !isUnstageble

            checkRow.background = if (isSelected) list.selectionBackground else list.background
            return checkRow
        }
    }
}
