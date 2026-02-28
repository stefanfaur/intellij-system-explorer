package ro.faur.explorer.gitpanel.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import ro.faur.explorer.gitpanel.ActiveBrowserTracker
import ro.faur.explorer.gitpanel.BackendType
import ro.faur.explorer.gitpanel.GitBackend
import ro.faur.explorer.gitpanel.GitRepositoryRegistry
import ro.faur.explorer.gitpanel.LocalGitBackend
import ro.faur.explorer.remote.git.GitLogEntry
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class GitPanelComponent(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val registry = GitRepositoryRegistry.getInstance(project)

    companion object {
        private val LOG = Logger.getInstance(GitPanelComponent::class.java)
    }

    @Volatile private var disposed = false
    @Volatile private var pullInProgress = false

    // ── State ──────────────────────────────────────────────────────────────
    private var selectedBackend: GitBackend? = null
    private var currentBranch: String? = null
    private var logEntries: List<GitLogEntry> = emptyList()

    // ── Toolbar widgets ────────────────────────────────────────────────────
    private val repoCombo = JComboBox<GitBackend>()
    private val branchLabel = JBLabel("")

    // ── Content panels (replaced in later phases) ──────────────────────────
    internal val commitLogPanel   = CommitLogPanel()
    internal val changedFilesPanel = ChangedFilesPanel()
    internal val commitDetailsPanel = CommitDetailsPanel()

    // Suppress action listener during programmatic model rebuild to avoid double reload
    private var suppressComboAction = false

    private val registryListener: () -> Unit = {
        if (!disposed) ApplicationManager.getApplication().invokeLater { if (!disposed) rebuildCombo() }
    }

    private val navListener: (String?, String) -> Unit = { connName, path ->
        if (!disposed) ApplicationManager.getApplication().invokeLater { if (!disposed) selectMatchingBackend(connName, path) }
    }

    init {
        buildToolbar()
        buildCenter()
        registry.addListener(registryListener)
        ActiveBrowserTracker.getInstance(project).addListener(navListener)
        repoCombo.addActionListener {
            if (suppressComboAction) return@addActionListener
            val selected = repoCombo.selectedItem as? GitBackend
            if (selected != selectedBackend) {
                selectedBackend = selected
                reloadData()
            }
        }
        // Load any backends that were registered before this panel was created
        rebuildCombo()
    }

    // ── Toolbar ─────────────────────────────────────────────────────────────

    private fun buildToolbar() {
        val toolbarPanel = JPanel(BorderLayout())

        // Combo renderer
        repoCombo.renderer = object : ListCellRenderer<GitBackend> {
            private val delegate = javax.swing.DefaultListCellRenderer()
            override fun getListCellRendererComponent(
                list: JList<out GitBackend>?, value: GitBackend?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val label = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    as javax.swing.JLabel
                label.text = value?.displayName ?: ""
                return label
            }
        }

        val group = DefaultActionGroup()
        group.add(object : AnAction("Refresh", "Reload git data", AllIcons.Actions.Refresh) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { reloadData() }
        })
        group.add(object : AnAction("Pull", "Pull current branch from remote", AllIcons.Vcs.Fetch) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { doPull() }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedBackend != null && !pullInProgress
            }
        })
        group.add(object : AnAction("Push", "Push current branch to remote", AllIcons.Actions.Upload) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { doPush() }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
        })
        group.add(object : AnAction("Add Local Repo", "Add a local git repository", AllIcons.General.Add) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { addLocalRepo() }
        })
        group.add(object : AnAction("Remove Repo", "Remove selected repository", AllIcons.General.Remove) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { removeSelectedRepo() }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
        })

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("GitPanel.Toolbar", group, true)
        toolbar.targetComponent = this

        val comboRow = JPanel(BorderLayout())
        comboRow.add(repoCombo, BorderLayout.CENTER)
        comboRow.add(branchLabel, BorderLayout.EAST)

        toolbarPanel.add(comboRow, BorderLayout.CENTER)
        toolbarPanel.add(toolbar.component, BorderLayout.EAST)
        add(toolbarPanel, BorderLayout.NORTH)
    }

    // ── Center layout ────────────────────────────────────────────────────────

    private fun buildCenter() {
        val bottomSplit = JBSplitter(false, 0.4f)
        bottomSplit.firstComponent  = changedFilesPanel
        bottomSplit.secondComponent = commitDetailsPanel

        val mainSplit = JBSplitter(true, 0.65f)
        mainSplit.firstComponent  = commitLogPanel
        mainSplit.secondComponent = bottomSplit

        add(mainSplit, BorderLayout.CENTER)

        // Wire commit selection
        commitLogPanel.onCommitSelected = { entry -> onCommitSelected(entry) }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private fun addLocalRepo() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Git Repository Root"
        FileChooser.chooseFile(descriptor, project, null) { vf ->
            if (File(vf.path, ".git").exists()) {
                registry.register(LocalGitBackend(vf.path))
            } else {
                Notifications.Bus.notify(Notification(
                    "SystemExplorer",
                    "Not a Git Repository",
                    "The selected folder '${vf.path}' does not contain a .git directory.",
                    NotificationType.WARNING
                ), project)
            }
        }
    }

    private fun removeSelectedRepo() {
        val backend = selectedBackend ?: return
        registry.unregister(backend.id)
    }

    // ── Combo rebuild ────────────────────────────────────────────────────────

    private fun rebuildCombo() {
        val all = registry.getAll().sortedWith(compareBy(
            { if (it.id.type == BackendType.LOCAL) 0 else 1 },
            { it.displayName }
        ))
        val prevKey = selectedBackend?.id?.key
        suppressComboAction = true
        try {
            val model = DefaultComboBoxModel<GitBackend>()
            all.forEach { model.addElement(it) }
            repoCombo.model = model
            val toSelect = all.firstOrNull { it.id.key == prevKey } ?: all.firstOrNull()
            repoCombo.selectedItem = toSelect
        } finally {
            suppressComboAction = false
        }
        val newSelected = repoCombo.selectedItem as? GitBackend
        if (newSelected?.id?.key != prevKey) {
            selectedBackend = newSelected
            reloadData()
        }
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    fun reloadData() {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val branch = runCatching { backend.getCurrentBranch() }.getOrElse { null }
            val log    = runCatching { backend.getLog(200) }.getOrElse { emptyList() }
            val status = runCatching { backend.getWorkingTreeStatus() }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater  // stale
                currentBranch = branch
                logEntries    = log
                branchLabel.text = if (branch != null) "  \u2387 $branch  " else ""
                commitLogPanel.setData(log, status.size)
                // branch == null means getCurrentBranch() failed — a strong signal that
                // git is not reachable on the remote server (wrong PATH, not installed, etc.).
                // Notify the user so they are not left wondering why commits are missing.
                if (branch == null && backend.id.type == BackendType.REMOTE) {
                    Notifications.Bus.notify(
                        Notification(
                            "SystemExplorer",
                            "Remote Git: Could Not Load Repository",
                            "Failed to run git commands for <b>${backend.displayName}</b>. " +
                                "Make sure git is installed and on the PATH on the remote server. " +
                                "Check the IDE log for details.",
                            NotificationType.WARNING
                        ),
                        project
                    )
                }
            }
        }
    }

    // ── Backend auto-selection ────────────────────────────────────────────────

    fun selectMatchingBackend(connectionName: String?, currentPath: String) {
        val all = registry.getAll()
        val match = if (connectionName == null) {
            all.filter { it.id.type == BackendType.LOCAL }
                .filter { currentPath == it.repoPath || currentPath.startsWith(it.repoPath + "/") }
                .maxByOrNull { it.repoPath.length }
        } else {
            all.filter { it.id.type == BackendType.REMOTE && it.id.connectionName == connectionName }
                .filter { currentPath == it.repoPath || currentPath.startsWith(it.repoPath + "/") }
                .maxByOrNull { it.repoPath.length }
        }
        if (match != null && match.id.key != selectedBackend?.id?.key) {
            repoCombo.selectedItem = match
        }
    }

    // ── Selection handling ────────────────────────────────────────────────────

    private fun onCommitSelected(entry: GitLogEntry?) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        if (entry == null) {
            // Working tree row — switch both panels to staging/editor mode
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val files = backend.getWorkingTreeStatus()
                    ApplicationManager.getApplication().invokeLater {
                        if (disposed) return@invokeLater
                        if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                        changedFilesPanel.setMode(staging = true)
                        changedFilesPanel.setFiles(files, "Working Tree — ${files.size} changes")
                        commitDetailsPanel.setWorkingTreeMode(currentBranch) { message, push ->
                            doCommit(message, push)
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to load working tree status", e)
                }
            }
        } else {
            // History row — switch both panels back to read-only mode
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val files = backend.getCommitFiles(entry.hash)
                    val info  = backend.getCommitInfo(entry.hash)
                    ApplicationManager.getApplication().invokeLater {
                        if (disposed) return@invokeLater
                        if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                        changedFilesPanel.setMode(staging = false)
                        changedFilesPanel.setFiles(files, "Changes in ${entry.hash.take(7)}")
                        commitDetailsPanel.setCommit(info)
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to load commit details for backend ${backend.javaClass.simpleName}", e)
                }
            }
        }
    }

    // ── Write operations ─────────────────────────────────────────────────────

    private fun doCommit(message: String, push: Boolean) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        val paths = changedFilesPanel.getCheckedPaths()

        // Validate on EDT before going to background
        if (paths.isEmpty()) {
            commitDetailsPanel.showError("No files selected. Check at least one file to commit.")
            return
        }
        if (message.isBlank()) {
            commitDetailsPanel.showError("Commit message cannot be empty.")
            return
        }

        // For remote + push: confirm on EDT
        if (push && backend.id.type == BackendType.REMOTE) {
            val confirmed = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "Push to remote '${backend.id.connectionName}' on branch '${currentBranch ?: "?"}'?\nThis cannot be undone.",
                "Confirm Push",
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            ) == javax.swing.JOptionPane.OK_OPTION
            if (!confirmed) return
        }

        commitDetailsPanel.showProgress()

        ApplicationManager.getApplication().executeOnPooledThread {
            val stageResult = backend.stageFiles(paths)
            if (!stageResult.isSuccess) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey)
                        commitDetailsPanel.showError("Stage failed: ${stageResult.stderr.takeLast(200)}")
                }
                return@executeOnPooledThread
            }

            val commitResult = backend.commit(message)
            if (!commitResult.isSuccess) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey)
                        commitDetailsPanel.showError("Commit failed: ${commitResult.stderr.takeLast(200)}")
                }
                return@executeOnPooledThread
            }

            if (push) {
                val pushResult = backend.push()
                if (!pushResult.isSuccess) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey) {
                            commitDetailsPanel.showError(
                                "Committed locally. Push failed: ${pushResult.stderr.takeLast(200)}. " +
                                "Retry with the Push toolbar button."
                            )
                            reloadData()
                        }
                    }
                    return@executeOnPooledThread
                }
            }

            ApplicationManager.getApplication().invokeLater {
                if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey) {
                    commitDetailsPanel.clearEditor()
                    reloadData()
                }
            }
        }
    }

    private fun doPush() {
        val backend = selectedBackend ?: return

        // Confirmation is required only for REMOTE backends: SSH pushes cross a network boundary
        // and may involve credentials/access controls that the user should consciously approve.
        // Local pushes go to a local upstream and are trivially recoverable.
        if (backend.id.type == BackendType.REMOTE) {
            val confirmed = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "Push to remote '${backend.id.connectionName}' on branch '${currentBranch ?: "?"}'?\nThis cannot be undone.",
                "Confirm Push",
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            ) == javax.swing.JOptionPane.OK_OPTION
            if (!confirmed) return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.push()
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (!result.isSuccess) {
                    Notifications.Bus.notify(
                        Notification(
                            "SystemExplorer",
                            "Push Failed",
                            result.stderr.takeLast(300).ifBlank { "Unknown error" },
                            NotificationType.ERROR
                        ), project
                    )
                } else {
                    reloadData()
                }
            }
        }
    }

    private fun doPull() {
        val backend = selectedBackend ?: return
        if (pullInProgress) return
        pullInProgress = true

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Pulling\u2026", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val result = backend.pull()
                ApplicationManager.getApplication().invokeLater {
                    pullInProgress = false   // ALWAYS reset before disposed check
                    if (disposed) return@invokeLater
                    when {
                        !result.isSuccess -> {
                            Notifications.Bus.notify(
                                Notification(
                                    "SystemExplorer",
                                    "Pull Failed",
                                    result.stderr.takeLast(300).ifBlank { "Unknown error" },
                                    NotificationType.ERROR
                                ), project
                            )
                        }
                        result.stdout.contains("CONFLICT") -> {
                            Notifications.Bus.notify(
                                Notification(
                                    "SystemExplorer",
                                    "Pull Conflicts",
                                    "Pull resulted in conflicts \u2014 resolve the marked files",
                                    NotificationType.WARNING
                                ), project
                            )
                            reloadData()
                        }
                        else -> reloadData()
                    }
                }
            }
        })
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    override fun dispose() {
        disposed = true
        registry.removeListener(registryListener)
        ActiveBrowserTracker.getInstance(project).removeListener(navListener)
    }
}
