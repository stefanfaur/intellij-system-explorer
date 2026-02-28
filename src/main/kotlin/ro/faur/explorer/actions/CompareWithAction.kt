package ro.faur.explorer.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Compare exactly two selected files side by side using IntelliJ's DiffManager.
 *
 * Enabled only when exactly two files are selected in the System Explorer file tree.
 * Left file = top row in tree order, right file = bottom row.
 */
class CompareWithAction : AnAction("Compare With...") {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabledAndVisible = tree != null
            && ExplorerActionUtil.isExplorerActive(e)
            && tree.getSelectedFiles().size == 2
    }

    override fun actionPerformed(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val project = e.project ?: return
        val selected = tree.getSelectedFiles()
        if (selected.size != 2) return

        val leftVf = LocalFileSystem.getInstance().findFileByPath(selected[0].path) ?: return
        val rightVf = LocalFileSystem.getInstance().findFileByPath(selected[1].path) ?: return
        leftVf.refresh(false, false)
        rightVf.refresh(false, false)

        val factory = DiffContentFactory.getInstance()
        val leftContent = factory.create(project, leftVf)
        val rightContent = factory.create(project, rightVf)

        val request = SimpleDiffRequest(
            "Compare: ${selected[0].name} vs ${selected[1].name}",
            leftContent, rightContent,
            selected[0].name, selected[1].name
        )

        DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
    }
}
