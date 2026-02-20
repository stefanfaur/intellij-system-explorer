package ro.faur.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import ro.faur.explorer.quickopen.ui.QuickOpenPopup
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.ui.QuickOpenDialog

/**
 * Action that opens the Quick Open Directory dialog (Cmd+Shift+P).
 *
 * Works globally — the System Explorer does not need to be focused.
 * When the user confirms a valid directory path, opens/activates the
 * System Explorer and navigates to that directory.
 *
 * Branches on [ExplorerSettings.State.quickOpenV2Enabled]:
 *   - true  → new non-modal [QuickOpenPopup] with fuzzy search
 *   - false → legacy modal [QuickOpenDialog]
 */
class QuickOpenAction : AnAction("Quick Open Directory") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = ExplorerActionUtil.findExplorerPanel(e)
            ?: ExplorerActionUtil.activateAndFindPanel(project)
            ?: return

        val settings = ExplorerSettings.getInstance()
        if (settings.state.quickOpenV2Enabled) {
            val initialQuery = getInitialQueryFromEditor(project)
            QuickOpenPopup.show(project, panel, initialQuery)
        } else {
            val dialog = QuickOpenDialog(project, panel.getNavigationHistory())
            if (dialog.showAndGet()) {
                val path = dialog.getSelectedPath()
                if (path.isNotEmpty()) {
                    panel.navigateTo(path)
                    ToolWindowManager.getInstance(project)
                        .getToolWindow(ExplorerActionUtil.TOOL_WINDOW_ID)?.show()
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Enable whenever a project is available (global action)
        e.presentation.isEnabledAndVisible = e.project != null
    }

    /**
     * Returns the selected text (if any) or the path-like token at the caret
     * in the currently active text editor. Returns blank if no editor is active
     * or no meaningful path token is found.
     */
    private fun getInitialQueryFromEditor(project: com.intellij.openapi.project.Project): String {
        val editor = com.intellij.openapi.fileEditor.FileEditorManager
            .getInstance(project).selectedTextEditor ?: return ""

        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            return selectionModel.selectedText?.trim() ?: ""
        }

        val offset = editor.caretModel.offset
        val text = editor.document.immutableCharSequence
        if (offset < 0 || offset >= text.length) return ""

        val pathChars = Regex("[a-zA-Z0-9/._\\-~\$]")
        if (!pathChars.matches(text[offset].toString())) return ""

        var start = offset
        var end = offset
        while (start > 0 && pathChars.matches(text[start - 1].toString())) start--
        while (end < text.length - 1 && pathChars.matches(text[end + 1].toString())) end++

        val token = text.substring(start, end + 1)
        return if (token.contains('/') || token.contains('.') ||
                   token.startsWith("~") || token.startsWith("$")) {
            token
        } else {
            ""
        }
    }
}
