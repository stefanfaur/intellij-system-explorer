package ro.faur.explorer.gitpanel.ui

import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import ro.faur.explorer.remote.git.GitLogEntry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.util.Date
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class CommitLogPanel : JPanel(BorderLayout()) {

    var onCommitSelected: ((GitLogEntry?) -> Unit)? = null

    private val model = CommitLogTableModel()
    private val table = JBTable(model)
    private var allEntries: List<GitLogEntry> = emptyList()
    private var workingTreeCount: Int = 0

    init {
        val filter = SearchTextField(false)

        filter.textEditor.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) { applyFilter(filter.text) }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) { applyFilter(filter.text) }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) { applyFilter(filter.text) }
        })

        setupTable()

        add(filter, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun setupTable() {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.setShowGrid(false)
        table.intercellSpacing = java.awt.Dimension(0, 0)
        table.rowHeight = 22
        table.tableHeader.reorderingAllowed = false
        table.fillsViewportHeight = true

        // Column widths
        CommitLogTableModel.Column.entries.forEachIndexed { idx, col ->
            table.columnModel.getColumn(idx).preferredWidth = col.preferredWidth
        }
        // Subject column stretches
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN

        // Graph column renderer
        table.columnModel.getColumn(CommitLogTableModel.Column.GRAPH.ordinal).cellRenderer =
            GraphDotRenderer()

        // Subject column renderer
        table.columnModel.getColumn(CommitLogTableModel.Column.SUBJECT.ordinal).cellRenderer =
            SubjectRenderer()

        // Date column renderer
        table.columnModel.getColumn(CommitLogTableModel.Column.DATE.ordinal).cellRenderer =
            RelativeDateRenderer()

        // Selection callback
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) {
                    onCommitSelected?.invoke(model.getLogEntry(row))
                }
            }
        }
    }

    fun setData(log: List<GitLogEntry>, workingTreeChangeCount: Int) {
        allEntries = log
        workingTreeCount = workingTreeChangeCount
        model.setData(log, workingTreeChangeCount)
    }

    private fun applyFilter(query: String) {
        if (query.isBlank()) {
            model.setData(allEntries, workingTreeCount)
        } else {
            val q = query.lowercase()
            val filtered = allEntries.filter {
                it.subject.lowercase().contains(q) || it.authorName.lowercase().contains(q)
            }
            model.setData(filtered, workingTreeCount)
        }
    }

    // ── Cell renderers ───────────────────────────────────────────────────────

    private inner class GraphDotRenderer : TableCellRenderer {
        private val label = javax.swing.JLabel("●", SwingConstants.CENTER)

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
        ): Component {
            val dotColor: Color = when {
                model.isWorkingTreeRow(row) -> JBColor(Color(0xCC, 0x88, 0x00), Color(0xFF, 0xBB, 0x33))
                row == 1                    -> JBColor(Color(0x00, 0x8C, 0x00), Color(0x59, 0xA8, 0x69))
                else                        -> JBColor(Color(0x88, 0x88, 0x88), Color(0x66, 0x66, 0x66))
            }
            label.foreground = dotColor
            if (isSelected) label.background = table.selectionBackground
            else            label.background = table.background
            label.isOpaque = true
            return label
        }
    }

    private inner class SubjectRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
            font = if (model.isWorkingTreeRow(row)) {
                font.deriveFont(java.awt.Font.ITALIC)
            } else {
                font.deriveFont(java.awt.Font.PLAIN)
            }
            return this
        }
    }

    class RelativeDateRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
        ): Component {
            val displayValue = when (value) {
                is Date -> format(value.time / 1000)
                else    -> value?.toString() ?: ""
            }
            return super.getTableCellRendererComponent(table, displayValue, isSelected, hasFocus, row, col)
        }

        companion object {
            fun format(timestamp: Long, now: Long = System.currentTimeMillis() / 1000): String {
                val diffSec = now - timestamp
                return when {
                    diffSec < 60          -> "just now"
                    diffSec < 3600        -> "${diffSec / 60} min ago"
                    diffSec < 86400       -> "${diffSec / 3600} h ago"
                    diffSec < 604800      -> "${diffSec / 86400} days ago"
                    diffSec < 2592000     -> "${diffSec / 604800} weeks ago"
                    diffSec < 31536000    -> "${diffSec / 2592000} months ago"
                    else                  -> "${diffSec / 31536000} years ago"
                }
            }
        }
    }
}
