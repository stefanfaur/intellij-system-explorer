package ro.faur.explorer.gitpanel.ui

import ro.faur.explorer.remote.git.GitLogEntry
import javax.swing.table.AbstractTableModel

class CommitLogTableModel : AbstractTableModel() {

    private var entries: List<GitLogEntry> = emptyList()
    private var workingTreeCount: Int = 0

    enum class Column(val title: String, val preferredWidth: Int) {
        GRAPH("", 24),
        SUBJECT("Subject", 400),
        AUTHOR("Author", 120),
        DATE("Date", 100),
    }

    fun setData(log: List<GitLogEntry>, workingTreeChangeCount: Int) {
        entries = log
        workingTreeCount = workingTreeChangeCount
        fireTableDataChanged()
    }

    fun getWorkingTreeCount() = workingTreeCount

    // Row 0 = "Working Tree" virtual row, rows 1..n = log entries
    override fun getRowCount(): Int = entries.size + 1
    override fun getColumnCount(): Int = Column.entries.size
    override fun getColumnName(col: Int): String = Column.entries[col].title
    override fun isCellEditable(row: Int, col: Int): Boolean = false

    fun getLogEntry(row: Int): GitLogEntry? =
        if (row == 0) null else entries.getOrNull(row - 1)

    fun isWorkingTreeRow(row: Int): Boolean = row == 0

    override fun getValueAt(row: Int, col: Int): Any? {
        val column = Column.entries[col]
        if (row == 0) {
            return when (column) {
                Column.GRAPH   -> "●"
                Column.SUBJECT -> "Working Tree Changes ($workingTreeCount files)"
                Column.AUTHOR  -> ""
                Column.DATE    -> ""
            }
        }
        val entry = entries.getOrNull(row - 1) ?: return null
        return when (column) {
            Column.GRAPH   -> "●"
            Column.SUBJECT -> entry.subject
            Column.AUTHOR  -> entry.authorName
            Column.DATE    -> java.util.Date(entry.timestamp * 1_000L)
        }
    }
}
