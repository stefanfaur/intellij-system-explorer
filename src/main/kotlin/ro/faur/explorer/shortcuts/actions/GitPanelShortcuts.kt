package ro.faur.explorer.shortcuts.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import ro.faur.explorer.gitpanel.GitBackend
import ro.faur.explorer.gitpanel.exec.GitCommandResult
import ro.faur.explorer.gitpanel.ui.GitPanelComponent
import ro.faur.explorer.shortcuts.ChordToast
import ro.faur.explorer.shortcuts.PanelContext

/**
 * Action implementations for Git panel chord shortcuts.
 * 
 * These actions are triggered by chord sequences (e.g., `c for cherry-pick).
 * They delegate to the GitPanelComponent's methods.
 */
object GitPanelShortcuts {

    /**
     * Executes a Git panel action.
     */
    fun executeAction(project: Project, actionId: String, gitPanelComponent: GitPanelComponent?) {
        if (gitPanelComponent == null) {
            ChordToast.showActionFailed(actionId, "Git panel not available")
            return
        }

        when (actionId) {
            "cherryPick" -> doCherryPick(project, gitPanelComponent)
            "revertChanges" -> doRevertChanges(project, gitPanelComponent)
            "renameBranch" -> doRenameBranch(project, gitPanelComponent)
            "newBranch" -> doNewBranch(project, gitPanelComponent)
            "fetch" -> doFetch(project, gitPanelComponent)
            "pull" -> doPull(project, gitPanelComponent)
            "copyCommitHash" -> doCopyCommitHash(project, gitPanelComponent)
            else -> ChordToast.showActionFailed(actionId, "Unknown Git action")
        }
    }

    /**
     * Cherry-pick the selected commit.
     */
    private fun doCherryPick(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("cherryPick", "No repository selected")
            return
        }

        val selectedCommit = component.commitLogPanel.selectedCommit
        if (selectedCommit == null) {
            ChordToast.showActionFailed("cherryPick", "No commit selected")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            // git cherry-pick <hash>
            val result = backend.execGit("cherry-pick", selectedCommit.hash)
            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    showNotification(project, "Cherry-pick", "Successfully cherry-picked ${selectedCommit.hash.take(7)}", NotificationType.INFORMATION)
                    component.reloadData()
                } else {
                    ChordToast.showActionFailed("cherryPick", result.stderr.ifBlank { "Cherry-pick failed" })
                }
            }
        }
    }

    /**
     * Revert the selected commit (create a new commit that undoes it).
     */
    private fun doRevertChanges(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("revertChanges", "No repository selected")
            return
        }

        val selectedCommit = component.commitLogPanel.selectedCommit
        if (selectedCommit == null) {
            ChordToast.showActionFailed("revertChanges", "No commit selected")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            // git revert --no-commit <hash>
            val result = backend.execGit("revert", "--no-commit", selectedCommit.hash)
            if (result.isSuccess) {
                // Commit the revert
                val commitResult = backend.execGit("commit", "-m", "Revert \"${selectedCommit.subject}\"")
                ApplicationManager.getApplication().invokeLater {
                    if (commitResult.isSuccess) {
                        showNotification(project, "Revert", "Successfully reverted ${selectedCommit.hash.take(7)}", NotificationType.INFORMATION)
                        component.reloadData()
                    } else {
                        ChordToast.showActionFailed("revertChanges", commitResult.stderr.ifBlank { "Revert commit failed" })
                    }
                }
            } else {
                ApplicationManager.getApplication().invokeLater {
                    ChordToast.showActionFailed("revertChanges", result.stderr.ifBlank { "Revert failed" })
                }
            }
        }
    }

    /**
     * Rename the current branch.
     */
    private fun doRenameBranch(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("renameBranch", "No repository selected")
            return
        }

        val currentBranch = component.currentBranch
        if (currentBranch == null) {
            ChordToast.showActionFailed("renameBranch", "Not on a branch")
            return
        }

        val newName = com.intellij.openapi.ui.Messages.showInputDialog(
            "Enter new branch name:",
            "Rename Branch",
            com.intellij.openapi.ui.Messages.getQuestionIcon(),
            currentBranch,
            null
        )

        if (newName.isNullOrBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            // git branch -m <old> <new>
            val result = backend.execGit("branch", "-m", currentBranch, newName)
            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    showNotification(project, "Rename Branch", "Renamed to '$newName'", NotificationType.INFORMATION)
                    component.reloadData()
                } else {
                    ChordToast.showActionFailed("renameBranch", result.stderr.ifBlank { "Rename failed" })
                }
            }
        }
    }

    /**
     * Create a new branch.
     */
    private fun doNewBranch(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("newBranch", "No repository selected")
            return
        }

        // Delegate to the existing doCreateBranch method via reflection
        try {
            val method = component.javaClass.getDeclaredMethod("doCreateBranch")
            method.isAccessible = true
            method.invoke(component)
        } catch (e: Exception) {
            ChordToast.showActionFailed("newBranch", "Could not create branch: ${e.message}")
        }
    }

    /**
     * Fetch from remote.
     */
    private fun doFetch(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("fetch", "No repository selected")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.execGit("fetch", "--all")
            ApplicationManager.getApplication().invokeLater {
                if (result.isSuccess) {
                    showNotification(project, "Fetch", "Fetched from remote", NotificationType.INFORMATION)
                    component.reloadData()
                } else {
                    ChordToast.showActionFailed("fetch", result.stderr.ifBlank { "Fetch failed" })
                }
            }
        }
    }

    /**
     * Pull from remote.
     */
    private fun doPull(project: Project, component: GitPanelComponent) {
        val backend = component.selectedBackend
        if (backend == null) {
            ChordToast.showActionFailed("pull", "No repository selected")
            return
        }

        // Delegate to the existing doPull method via reflection
        try {
            val method = component.javaClass.getDeclaredMethod("doPull")
            method.isAccessible = true
            method.invoke(component)
        } catch (e: Exception) {
            ChordToast.showActionFailed("pull", "Could not pull: ${e.message}")
        }
    }

    /**
     * Copy the selected commit hash to clipboard.
     */
    private fun doCopyCommitHash(project: Project, component: GitPanelComponent) {
        val selectedCommit = component.commitLogPanel.selectedCommit
        if (selectedCommit == null) {
            ChordToast.showActionFailed("copyCommitHash", "No commit selected")
            return
        }

        copyToClipboard(selectedCommit.hash)
        showNotification(project, "Copy", "Copied ${selectedCommit.hash.take(7)}", NotificationType.INFORMATION)
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("SystemExplorer", title, content, type),
            project
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val selection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(selection, selection)
    }

    /**
     * Extension to execute arbitrary git commands on a backend.
     * This is a workaround until cherry-pick, revert, etc. are added to GitBackend interface.
     */
    private fun GitBackend.execGit(vararg args: String): GitCommandResult {
        // Build the command string
        val cmd = args.joinToString(" ")
        return try {
            // Try to use the backend's existing methods if available
            // For now, use execGitCommand on LocalGitBackend
            return when (this) {
                is ro.faur.explorer.gitpanel.LocalGitBackend -> {
                    execGitCommand(cmd)
                }
                else -> {
                    GitCommandResult(1, "", "Backend does not support arbitrary git commands")
                }
            }
        } catch (e: Exception) {
            GitCommandResult(1, "", e.message ?: "Unknown error")
        }
    }
}

// Extension properties for GitPanelComponent to expose internal state via reflection
val GitPanelComponent.selectedBackend: GitBackend?
    get() {
        return try {
            val field = this.javaClass.getDeclaredField("selectedBackend")
            field.isAccessible = true
            field.get(this) as? GitBackend
        } catch (e: Exception) {
            null
        }
    }

val GitPanelComponent.currentBranch: String?
    get() {
        return try {
            val field = this.javaClass.getDeclaredField("currentBranch")
            field.isAccessible = true
            field.get(this) as? String
        } catch (e: Exception) {
            null
        }
    }

// Extension property for CommitLogPanel to get the selected commit
val ro.faur.explorer.gitpanel.ui.CommitLogPanel.selectedCommit: ro.faur.explorer.remote.git.GitLogEntry?
    get() {
        return try {
            val tableField = this.javaClass.getDeclaredField("table")
            tableField.isAccessible = true
            val table = tableField.get(this) as? javax.swing.JTable
            val selectedRow = table?.selectedRow ?: return null
            if (selectedRow < 0) return null
            
            val modelField = this.javaClass.getDeclaredField("model")
            modelField.isAccessible = true
            val model = modelField.get(this) as? ro.faur.explorer.gitpanel.ui.CommitLogTableModel
            model?.getLogEntry(selectedRow)
        } catch (e: Exception) {
            null
        }
    }
