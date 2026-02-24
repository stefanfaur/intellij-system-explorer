package ro.faur.explorer.quickopen.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.concurrency.AppExecutorUtil
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.util.FileSizeFormatter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JPanel

private const val CARD_EDITOR = "editor"
private const val CARD_DIR = "directory"
private const val MAX_FILE_BYTES = 512 * 1024L  // 512 KB

/**
 * Right-side pane shown when the user presses Tab.
 *
 * For FILE / CONTENT_MATCH: shows a full read-only EditorEx with syntax
 * highlighting, scrolled to the matched line with the match highlighted.
 * For DIRECTORY: shows the existing directory-children snapshot.
 */
class PreviewPane(private val project: Project) : JBPanel<PreviewPane>(BorderLayout()), Disposable {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    // --- Editor card ---
    private val editorDocument = EditorFactory.getInstance().createDocument("")
    private val editor: EditorEx = (EditorFactory.getInstance()
        .createEditor(editorDocument, project, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, true) as EditorEx)
        .also { ed ->
            ed.settings.apply {
                isLineNumbersShown = true
                isRightMarginShown = false
                isFoldingOutlineShown = false
                isIndentGuidesShown = false
                isCaretRowShown = false
                additionalLinesCount = 0
            }
        }

    // --- Directory card ---
    private val dirPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    init {
        cardPanel.add(editor.component, CARD_EDITOR)
        cardPanel.add(dirPanel, CARD_DIR)
        add(cardPanel, BorderLayout.CENTER)
        preferredSize = Dimension(380, 0)
        minimumSize = Dimension(280, 0)
    }

    fun update(candidate: SearchCandidate) {
        when (candidate.type) {
            CandidateType.DIRECTORY -> showDirectory(candidate)
            else -> showFileEditor(candidate)
        }
    }

    private fun showDirectory(candidate: SearchCandidate) {
        AppExecutorUtil.getAppExecutorService().execute {
            val file = File(candidate.fullPath)
            val children = if (file.isDirectory) {
                file.listFiles()
                    ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
                    ?.take(20) ?: emptyList()
            } else emptyList()
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified())

            ApplicationManager.getApplication().invokeLater {
                dirPanel.removeAll()
                dirPanel.add(JBLabel(candidate.fullPath).apply { font = font.deriveFont(10f) })
                dirPanel.add(JBLabel("${children.size} items"))
                children.forEach { child ->
                    val icon = if (child.isDirectory) "[D]" else "   "
                    val size = if (!child.isDirectory) "  ${FileSizeFormatter.format(child.length())}" else ""
                    dirPanel.add(JBLabel("$icon ${child.name}$size").apply { font = font.deriveFont(11f) })
                }
                dirPanel.add(JBLabel("Modified: $modified").apply { font = font.deriveFont(10f) })
                cardLayout.show(cardPanel, CARD_DIR)
                revalidate(); repaint()
            }
        }
    }

    private fun showFileEditor(candidate: SearchCandidate) {
        // Show editor card immediately (clears old content)
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                editorDocument.setText("Loading…")
            }
            cardLayout.show(cardPanel, CARD_EDITOR)
            revalidate(); repaint()
        }

        AppExecutorUtil.getAppExecutorService().execute {
            val file = File(candidate.fullPath)
            if (!file.exists() || !file.isFile) {
                setEditorText("File not found: ${candidate.fullPath}", null, null, null)
                return@execute
            }
            if (file.length() > MAX_FILE_BYTES) {
                setEditorText("File too large for preview (> 512 KB)", null, null, null)
                return@execute
            }
            val content = try { file.readText() } catch (_: Exception) {
                setEditorText("Cannot read file.", null, null, null)
                return@execute
            }

            // Determine line number and match ranges for CONTENT_MATCH
            val targetLine = if (candidate.type == CandidateType.CONTENT_MATCH) {
                // displayName is "filename:lineNumber" — parse line number
                candidate.displayName.substringAfterLast(':').toIntOrNull()
            } else null
            val matchRanges = candidate.contentMatchRanges

            setEditorText(content, file.absolutePath, targetLine, matchRanges)
        }
    }

    private fun setEditorText(
        content: String,
        filePath: String?,
        targetLine: Int?,       // 1-based, or null
        matchRanges: List<IntRange>?
    ) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                editorDocument.setText(content)
            }

            // Attach syntax highlighter
            if (filePath != null) {
                val vf = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (vf != null) {
                    val highlighter = EditorHighlighterFactory.getInstance()
                        .createEditorHighlighter(project, vf)
                    editor.highlighter = highlighter
                } else {
                    val fileType = FileTypeManager.getInstance()
                        .getFileTypeByFileName(filePath.substringAfterLast('/'))
                    val highlighter = EditorHighlighterFactory.getInstance()
                        .createEditorHighlighter(project, fileType)
                    editor.highlighter = highlighter
                }
            }

            // Clear old highlights
            editor.markupModel.removeAllHighlighters()

            // Scroll to line and highlight match
            if (targetLine != null && targetLine > 0) {
                val lineIndex = (targetLine - 1).coerceIn(0, editorDocument.lineCount - 1)
                val lineStartOffset = editorDocument.getLineStartOffset(lineIndex)

                // Add range highlighters for each match
                if (!matchRanges.isNullOrEmpty()) {
                    val matchColor = JBColor(Color(0xFF, 0xD7, 0x80), Color(0x7A, 0x5F, 0x20))
                    val attrs = TextAttributes(null, matchColor, null, null, Font.BOLD)
                    for (range in matchRanges) {
                        val start = (lineStartOffset + range.first)
                            .coerceIn(0, editorDocument.textLength)
                        val end = (lineStartOffset + range.last + 1)
                            .coerceIn(0, editorDocument.textLength)
                        if (start < end) {
                            editor.markupModel.addRangeHighlighter(
                                start, end,
                                HighlighterLayer.SELECTION,
                                attrs,
                                HighlighterTargetArea.EXACT_RANGE
                            )
                        }
                    }
                }

                // Scroll after layout so dimensions are known
                ApplicationManager.getApplication().invokeLater {
                    editor.scrollingModel.scrollTo(
                        com.intellij.openapi.editor.LogicalPosition(lineIndex, 0),
                        ScrollType.CENTER
                    )
                }
            }

            revalidate(); repaint()
        }
    }

    fun clear() {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                editorDocument.setText("")
            }
            editor.markupModel.removeAllHighlighters()
            revalidate(); repaint()
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
