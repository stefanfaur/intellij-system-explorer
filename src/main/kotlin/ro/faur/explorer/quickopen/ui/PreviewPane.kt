package ro.faur.explorer.quickopen.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.util.FileSizeFormatter
import java.awt.BorderLayout
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Right-side pane shown when the user presses Tab.
 * Shows directory children snapshot or file metadata for the selected result.
 */
class PreviewPane : JBPanel<PreviewPane>(BorderLayout()) {

    private val contentPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    init {
        add(contentPanel, BorderLayout.CENTER)
        preferredSize = java.awt.Dimension(280, 0)
        minimumSize = java.awt.Dimension(200, 0)
    }

    fun update(candidate: SearchCandidate) {
        contentPanel.removeAll()
        val file = File(candidate.fullPath)

        if (!file.exists()) {
            contentPanel.add(JBLabel("Path does not exist"))
            revalidate(); repaint()
            return
        }

        contentPanel.add(JBLabel(candidate.fullPath).apply { font = font.deriveFont(10f) })
        contentPanel.add(JSeparator())

        if (file.isDirectory) {
            val children = file.listFiles()?.sortedWith(
                compareByDescending<File> { it.isDirectory }.thenBy { it.name }
            )?.take(12) ?: emptyList()
            contentPanel.add(JBLabel("${children.size} items"))
            children.forEach { child ->
                val icon = if (child.isDirectory) "📁" else "📄"
                val size = if (!child.isDirectory) "  ${FileSizeFormatter.format(child.length())}" else ""
                contentPanel.add(JBLabel("$icon ${child.name}$size").apply { font = font.deriveFont(11f) })
            }
            contentPanel.add(JSeparator())
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified())
            contentPanel.add(JBLabel("Modified: $modified").apply { font = font.deriveFont(10f) })
        } else {
            val sizeKb = file.length() / 1024
            val ext = file.extension.uppercase()
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified())
            contentPanel.add(JBLabel("Size: ${FileSizeFormatter.format(file.length())}  ($sizeKb KB)"))
            contentPanel.add(JBLabel("Language: $ext"))
            contentPanel.add(JBLabel("Modified: $modified"))
            contentPanel.add(JSeparator())
            // First 4 lines preview
            try {
                val lines = file.readLines().take(4)
                lines.forEach { line ->
                    contentPanel.add(JBLabel(line.take(50)).apply { font = font.deriveFont(10f) })
                }
            } catch (_: Exception) {}
        }

        revalidate()
        repaint()
    }

    fun clear() {
        contentPanel.removeAll()
        revalidate()
        repaint()
    }
}
