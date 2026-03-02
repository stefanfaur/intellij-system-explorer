package ro.faur.explorer.quickopen.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

class IndexBrowserDialog(
    private val manager: LuceneIndexManager,
    private val root: String,
) : DialogWrapper(null, false) {

    private val PAGE_SIZE = 500

    private val searchField = SearchTextField(false).apply {
        preferredSize = Dimension(300, preferredSize.height)
    }
    private val extCombo = JComboBox(DefaultComboBoxModel<String>())
    private val tableModel = object : DefaultTableModel(arrayOf("Path", "Ext", "Has Content"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(tableModel).apply {
        preferredScrollableViewportSize = Dimension(700, 300)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val snippetArea = JTextArea(6, 80).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11)
    }
    private val footerLabel = JBLabel("")
    private var currentPaths: List<String> = emptyList()
    private var offset = 0

    init {
        title = "Index Browser — ${root.replace(System.getProperty("user.home"), "~")}"
        setSize(800, 600)
        init()
        loadExtensions()
        loadPage(reset = true)

        searchField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = loadPage(reset = true)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = loadPage(reset = true)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = loadPage(reset = true)
        })
        extCombo.addActionListener { loadPage(reset = true) }
        table.selectionModel.addListSelectionListener { onRowSelected() }
    }

    override fun createCenterPanel(): JComponent {
        val filterPanel = JPanel().apply {
            add(JBLabel("Search:"))
            add(searchField)
            add(JBLabel("  Ext:"))
            add(extCombo)
        }

        val loadMoreBtn = JButton("Load more").apply {
            addActionListener { loadPage(reset = false) }
        }

        val tablePanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(loadMoreBtn, BorderLayout.SOUTH)
        }

        val snippetPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Content snippet:"), BorderLayout.NORTH)
            add(JBScrollPane(snippetArea), BorderLayout.CENTER)
        }

        return JPanel(BorderLayout(0, 6)).apply {
            preferredSize = Dimension(800, 580)
            add(filterPanel, BorderLayout.NORTH)
            add(tablePanel, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                add(snippetPanel, BorderLayout.CENTER)
                add(footerLabel, BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)
        }
    }

    override fun createActions() = arrayOf(okAction)

    private fun loadExtensions() {
        val exts = try { manager.listExtensions() } catch (_: Exception) { emptyList() }
        extCombo.model = DefaultComboBoxModel(arrayOf("(all)") + exts.toTypedArray())
    }

    private fun loadPage(reset: Boolean) {
        if (reset) {
            offset = 0
            while (tableModel.rowCount > 0) tableModel.removeRow(0)
        }
        SwingUtilities.invokeLater {
            try {
                val query = searchField.text.trim()
                val ext = (extCombo.selectedItem as? String)?.takeIf { it != "(all)" }

                val allPaths = manager.searchPaths(query, Int.MAX_VALUE)
                val filtered = if (ext != null)
                    allPaths.filter { it.substringAfterLast('.', "").lowercase() == ext }
                else allPaths

                currentPaths = filtered
                val page = filtered.drop(offset).take(PAGE_SIZE)
                page.forEach { path ->
                    val docExt = path.substringAfterLast('.', "")
                    val doc = try { manager.getDocument(path) } catch (_: Exception) { null }
                    tableModel.addRow(arrayOf(
                        path.replace(System.getProperty("user.home"), "~"),
                        docExt,
                        if (doc?.hasContent == true) "\u2713" else "\u2717"
                    ))
                }
                offset += page.size
                footerLabel.text = "  ${filtered.size} docs total  (showing $offset)"
            } catch (_: Exception) {}
        }
    }

    private fun onRowSelected() {
        val row = table.selectedRow
        if (row < 0) { snippetArea.text = ""; return }
        val shortPath = tableModel.getValueAt(row, 0) as? String ?: return
        val fullPath = shortPath.replace("~", System.getProperty("user.home"))
        SwingUtilities.invokeLater {
            try {
                val doc = manager.getDocument(fullPath)
                snippetArea.text = doc?.content?.take(2000) ?: "(no content indexed)"
                snippetArea.caretPosition = 0
            } catch (_: Exception) { snippetArea.text = "(error loading content)" }
        }
    }
}
