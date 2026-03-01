package ro.faur.explorer.gitpanel.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
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
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.vcsUtil.VcsUtil
import ro.faur.explorer.gitpanel.ActiveBrowserTracker
import ro.faur.explorer.gitpanel.BackendType
import ro.faur.explorer.gitpanel.BranchInfo
import ro.faur.explorer.gitpanel.CommitFile
import ro.faur.explorer.gitpanel.GitBackend
import ro.faur.explorer.gitpanel.GitRepositoryRegistry
import ro.faur.explorer.gitpanel.LocalGitBackend
import ro.faur.explorer.gitpanel.StashEntry
import ro.faur.explorer.remote.git.GitFileStatus
import ro.faur.explorer.remote.git.GitLogEntry
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class GitPanelComponent(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val registry = GitRepositoryRegistry.getInstance(project)

    companion object {
        private val LOG = Logger.getInstance(GitPanelComponent::class.java)
        private const val CARD_DETAILS = "details"
        private const val CARD_DIFF = "diff"
        private const val CARD_MAIN  = "main"
        private const val CARD_STASH = "stash"
    }

    @Volatile private var disposed = false
    @Volatile private var pullInProgress = false

    // ── State ──────────────────────────────────────────────────────────────
    private var selectedBackend: GitBackend? = null
    private var currentBranch: String? = null
    private var logEntries: List<GitLogEntry> = emptyList()
    private var selectedDiffFile: CommitFile? = null
    private var selectedCommitHash: String? = null  // non-null in history mode
    private var cachedBranches: List<BranchInfo> = emptyList()

    // ── Toolbar widgets ────────────────────────────────────────────────────
    private val repoCombo = JComboBox<GitBackend>()
    private val branchLabel = JBLabel("")

    // ── Content panels (replaced in later phases) ──────────────────────────
    internal val commitLogPanel   = CommitLogPanel()
    internal val changedFilesPanel = ChangedFilesPanel()
    internal val commitDetailsPanel = CommitDetailsPanel()
    private val inlineDiffPanel = InlineDiffPanel(project, this)
    private val rightSlotLayout = CardLayout()
    private val rightSlot = JPanel(rightSlotLayout)

    // ── Stash card ─────────────────────────────────────────────────────────
    private val mainViewLayout = CardLayout()
    private val mainView = JPanel(mainViewLayout)
    private val stashListPanel = StashListPanel()
    private val stashDiffPanel = InlineDiffPanel(project, this)
    private var onStashCard = false

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
        group.add(object : AnAction("Show Diff", "Open full-window diff for selected file", AllIcons.Actions.Diff) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { doShowFullDiff() }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedDiffFile != null && selectedBackend != null
            }
        })
        group.add(object : AnAction("Create Branch", "Create a new local branch from current HEAD", AllIcons.General.Add) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) { doCreateBranch() }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
        })
        group.add(object : AnAction("Delete Branch", "Delete a local branch", AllIcons.General.Remove) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) { showDeleteBranchSelectPopup(e) }
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedBackend != null && cachedBranches.any { !it.isCurrent }
            }
        })
        group.add(object : AnAction("Stash", "Create a stash of current working tree changes", AllIcons.Actions.MoveUp) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { doCreateStash() }
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
        })
        group.add(object : AnAction("Stash List", "Toggle stash list view", AllIcons.Actions.ListFiles) {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) { toggleStashCard() }
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

        branchLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        branchLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (cachedBranches.isNotEmpty()) showBranchPopup()
            }
        })

        toolbarPanel.add(comboRow, BorderLayout.CENTER)
        toolbarPanel.add(toolbar.component, BorderLayout.EAST)
        add(toolbarPanel, BorderLayout.NORTH)
    }

    private fun showBranchPopup() {
        val snapshot = cachedBranches
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(snapshot)
            .setTitle("Switch Branch")
            .setItemChosenCallback { branch: BranchInfo ->
                if (!branch.isCurrent) doCheckoutBranch(branch.name)
            }
            .setRenderer(object : ColoredListCellRenderer<BranchInfo>() {
                override fun customizeCellRenderer(
                    list: JList<out BranchInfo>,
                    value: BranchInfo,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    if (value.isCurrent) append("* ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            })
            .createPopup()
        popup.showUnderneathOf(branchLabel)
    }

    // ── Center layout ────────────────────────────────────────────────────────

    private fun buildCenter() {
        // Right slot: holds commitDetailsPanel (history mode) and inlineDiffPanel (staging mode)
        // CardLayout ensures only one is visible at a time
        rightSlot.add(commitDetailsPanel, CARD_DETAILS)
        rightSlot.add(inlineDiffPanel, CARD_DIFF)
        rightSlotLayout.show(rightSlot, CARD_DETAILS)

        val bottomSplit = JBSplitter(false, 0.4f)
        bottomSplit.firstComponent  = changedFilesPanel
        bottomSplit.secondComponent = rightSlot

        val mainSplit = JBSplitter(true, 0.65f)
        mainSplit.firstComponent  = commitLogPanel
        mainSplit.secondComponent = bottomSplit

        // Stash card: left = stash list, right = stash diff preview
        val stashView = JBSplitter(false, 0.35f)
        stashView.firstComponent  = stashListPanel
        stashView.secondComponent = stashDiffPanel

        mainView.add(mainSplit, CARD_MAIN)
        mainView.add(stashView, CARD_STASH)
        mainViewLayout.show(mainView, CARD_MAIN)

        add(mainView, BorderLayout.CENTER)

        // Wire stash list callbacks
        stashListPanel.onApply         = { entry -> doStashApply(entry) }
        stashListPanel.onPop           = { entry -> doStashPop(entry) }
        stashListPanel.onDrop          = { entry -> doStashDrop(entry) }
        stashListPanel.onStashSelected = { entry -> onStashEntrySelected(entry) }

        // Wire commit selection
        commitLogPanel.onCommitSelected = { entry -> onCommitSelected(entry) }

        // Wire file selection callbacks
        changedFilesPanel.onFileSelected = fileSelectedLambda@{ file ->
            selectedDiffFile = file
            if (selectedCommitHash != null) return@fileSelectedLambda  // history mode: stay in CommitDetailsPanel
            if (file != null) {
                showDiffSlot()
                loadInlineDiff(file)
            } else {
                inlineDiffPanel.clear()
            }
        }
        changedFilesPanel.onFileDoubleClicked = { file ->
            selectedDiffFile = file
            doShowFullDiff()
        }
    }

    // ── Slot visibility helpers ──────────────────────────────────────────────

    private fun showDiffSlot() {
        rightSlotLayout.show(rightSlot, CARD_DIFF)
    }

    private fun showCommitDetailsSlot() {
        inlineDiffPanel.clear()
        rightSlotLayout.show(rightSlot, CARD_DETAILS)
    }

    // ── Branch management ─────────────────────────────────────────────────────

    private fun doCheckoutBranch(targetBranch: String) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val status = runCatching { backend.getWorkingTreeStatus() }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (status.isNotEmpty()) {
                    showDirtyTreeDialog(backend, targetBranch, snapshotKey)
                } else {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        performCheckout(backend, targetBranch, snapshotKey)
                    }
                }
            }
        }
    }

    private fun showDirtyTreeDialog(backend: GitBackend, targetBranch: String, snapshotKey: Int) {
        val result = Messages.showDialog(
            project,
            "You have uncommitted changes. Switch to '$targetBranch'?",
            "Dirty Working Tree",
            arrayOf(
                "Stash & Switch",
                "Discard & Switch (WARNING: all uncommitted changes will be permanently lost)",
                "Cancel"
            ),
            2,
            Messages.getWarningIcon()
        )
        when (result) {
            0 -> doStashAndCheckout(backend, targetBranch, snapshotKey)
            1 -> doDiscardAndCheckout(backend, targetBranch, snapshotKey)
            else -> return
        }
    }

    private fun doStashAndCheckout(backend: GitBackend, targetBranch: String, snapshotKey: Int) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val stashResult = backend.stash("Auto-stash before checkout to $targetBranch", includeUntracked = true)
            if (!stashResult.isSuccess) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey)
                        notifyError("Stash failed: ${stashResult.stderr.takeLast(200)}")
                }
                return@executeOnPooledThread
            }
            performCheckout(backend, targetBranch, snapshotKey)
        }
    }

    private fun doDiscardAndCheckout(backend: GitBackend, targetBranch: String, snapshotKey: Int) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val resetResult = backend.resetHard()
            if (!resetResult.isSuccess) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && System.identityHashCode(selectedBackend) == snapshotKey)
                        notifyError("Reset failed: ${resetResult.stderr.takeLast(200)}")
                }
                return@executeOnPooledThread
            }
            performCheckout(backend, targetBranch, snapshotKey)
        }
    }

    private fun performCheckout(backend: GitBackend, targetBranch: String, snapshotKey: Int) {
        val result = backend.checkoutBranch(targetBranch)
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
            if (result.isSuccess) reloadData()
            else notifyError("Checkout failed: ${result.stderr.takeLast(300)}")
        }
    }

    private fun doCreateBranch() {
        val dialog = CreateBranchDialog(project)
        if (!dialog.showAndGet()) return
        val branchName = dialog.getBranchName()
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.createBranch(branchName)
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (result.isSuccess) reloadData()
                else notifyError("Create branch failed: ${result.stderr.takeLast(300)}")
            }
        }
    }

    private fun showDeleteBranchSelectPopup(e: AnActionEvent) {
        val deletable = cachedBranches.filter { !it.isCurrent }
        if (deletable.isEmpty()) {
            Messages.showInfoMessage(project, "No other local branches to delete.", "Delete Branch")
            return
        }
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(deletable)
            .setTitle("Select Branch to Delete")
            .setItemChosenCallback { branch: BranchInfo -> doDeleteBranch(branch.name) }
            .setRenderer(object : ColoredListCellRenderer<BranchInfo>() {
                override fun customizeCellRenderer(
                    list: JList<out BranchInfo>,
                    value: BranchInfo,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            })
            .createPopup()
        popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(e.dataContext))
    }

    private fun doDeleteBranch(branchName: String) {
        val confirmed = JOptionPane.showConfirmDialog(
            this,
            "Delete branch '$branchName'?",
            "Confirm Delete",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.OK_OPTION
        if (!confirmed) return
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.deleteBranch(branchName, force = false)
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                when {
                    result.isSuccess -> reloadData()
                    result.stderr.contains("not fully merged") -> {
                        val forceConfirmed = JOptionPane.showConfirmDialog(
                            this,
                            "Branch '$branchName' has commits not merged into the current branch.\n" +
                                "Force deleting will permanently lose these commits.\n\nForce delete anyway?",
                            "Unmerged Branch",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.ERROR_MESSAGE
                        ) == JOptionPane.OK_OPTION
                        if (forceConfirmed) {
                            ApplicationManager.getApplication().executeOnPooledThread {
                                val forceResult = backend.deleteBranch(branchName, force = true)
                                ApplicationManager.getApplication().invokeLater {
                                    if (disposed) return@invokeLater
                                    if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                                    if (forceResult.isSuccess) reloadData()
                                    else notifyError("Force delete failed: ${forceResult.stderr.takeLast(200)}")
                                }
                            }
                        }
                    }
                    else -> notifyError("Delete failed: ${result.stderr.takeLast(300)}")
                }
            }
        }
    }

    // ── Stash card navigation ─────────────────────────────────────────────────

    private fun toggleStashCard() {
        if (onStashCard) return  // already on stash card — no-op
        showStashCard()
    }

    private fun showStashCard() {
        onStashCard = true
        mainViewLayout.show(mainView, CARD_STASH)
        loadStashList()
    }

    private fun showMainCard() {
        onStashCard = false
        stashDiffPanel.clear()
        mainViewLayout.show(mainView, CARD_MAIN)
    }

    private fun loadStashList() {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val entries = runCatching { backend.stashList() }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                stashListPanel.setEntries(entries)
            }
        }
    }

    // ── Stash creation ────────────────────────────────────────────────────────

    private fun doCreateStash() {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val branch = runCatching { backend.getCurrentBranch() }.getOrElse { null } ?: "HEAD"
            val headLog = runCatching { backend.getLog(1) }.getOrElse { emptyList() }
            val hint = if (headLog.isNotEmpty()) {
                "WIP on $branch: ${headLog[0].hash.take(7)} ${headLog[0].subject}"
            } else {
                "WIP on $branch"
            }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                val dialog = CreateStashDialog(project, hint)
                if (!dialog.showAndGet()) return@invokeLater
                val message = dialog.getMessage()
                val includeUntracked = dialog.isIncludeUntracked()
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = backend.stash(message, includeUntracked)
                    ApplicationManager.getApplication().invokeLater {
                        if (disposed) return@invokeLater
                        if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                        if (result.isSuccess) {
                            showStashCard()
                        } else {
                            Notifications.Bus.notify(
                                Notification("SystemExplorer", "Stash Error",
                                    result.stderr.takeLast(200).ifBlank { "No local changes to save" },
                                    NotificationType.ERROR), project)
                        }
                    }
                }
            }
        }
    }

    // ── Stash actions ──────────────────────────────────────────────────────────

    private fun doStashApply(entry: StashEntry) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.stashApply(entry.index)
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (result.isSuccess) {
                    loadStashList()
                } else {
                    Notifications.Bus.notify(
                        Notification("SystemExplorer", "Stash Error",
                            "Apply failed: ${result.stderr.takeLast(200)}",
                            NotificationType.ERROR), project)
                }
            }
        }
    }

    private fun doStashPop(entry: StashEntry) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.stashPop(entry.index)
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (result.isSuccess) {
                    showMainCard()
                } else {
                    Notifications.Bus.notify(
                        Notification("SystemExplorer", "Stash Error",
                            "Pop failed: ${result.stderr.takeLast(200)}",
                            NotificationType.ERROR), project)
                }
            }
        }
    }

    private fun doStashDrop(entry: StashEntry) {
        val confirmed = JOptionPane.showConfirmDialog(
            this,
            "Delete stash '${entry.message}'?",
            "Confirm Drop",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.OK_OPTION
        if (!confirmed) return
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = backend.stashDrop(entry.index)
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (result.isSuccess) {
                    showMainCard()
                } else {
                    Notifications.Bus.notify(
                        Notification("SystemExplorer", "Stash Error",
                            "Drop failed: ${result.stderr.takeLast(200)}",
                            NotificationType.ERROR), project)
                }
            }
        }
    }

    // ── Stash diff preview ────────────────────────────────────────────────────

    private fun onStashEntrySelected(entry: StashEntry?) {
        if (entry == null) {
            stashDiffPanel.clear()
            return
        }
        // Per-file stash diff requires listing files changed in a stash which needs an
        // additional backend method not in scope for this phase. Show empty panel on selection.
        stashDiffPanel.clear()
    }

    private fun notifyError(msg: String) {
        Notifications.Bus.notify(
            Notification("SystemExplorer", "Branch Error", msg, NotificationType.ERROR),
            project
        )
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
            val branch   = runCatching { backend.getCurrentBranch() }.getOrElse { null }
            val log      = runCatching { backend.getLog(200) }.getOrElse { emptyList() }
            val status   = runCatching { backend.getWorkingTreeStatus() }.getOrElse { emptyList() }
            val branches = runCatching { backend.listBranches() }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater  // stale
                currentBranch = branch
                logEntries    = log
                cachedBranches = branches
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
                        selectedCommitHash = null
                        selectedDiffFile = null
                        changedFilesPanel.setMode(staging = true)
                        changedFilesPanel.setFiles(files, "Working Tree — ${files.size} changes")
                        commitDetailsPanel.setWorkingTreeMode(currentBranch) { message, push ->
                            doCommit(message, push)
                        }
                        showDiffSlot()
                        inlineDiffPanel.clear()
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
                        selectedCommitHash = entry.hash
                        selectedDiffFile = null
                        changedFilesPanel.setMode(staging = false)
                        changedFilesPanel.setFiles(files, "Changes in ${entry.hash.take(7)}")
                        commitDetailsPanel.setCommit(info)
                        showCommitDetailsSlot()
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

    // ── Inline diff ──────────────────────────────────────────────────────────

    private fun loadInlineDiff(commitFile: CommitFile) {
        val backend = selectedBackend ?: return
        val snapshotKey = System.identityHashCode(backend)

        inlineDiffPanel.showSpinner()

        ApplicationManager.getApplication().executeOnPooledThread {
            val headBytes: ByteArray? = when (commitFile.status) {
                GitFileStatus.UNTRACKED, GitFileStatus.ADDED -> null
                else -> backend.getHeadContent(commitFile.path)
            }
            val workBytes: ByteArray? = when (commitFile.status) {
                GitFileStatus.DELETED -> null
                else -> runCatching {
                    File(backend.repoPath, commitFile.path).readBytes()
                }.getOrNull()
            }

            val request = buildDiffRequest(backend.repoPath, commitFile, headBytes, workBytes)

            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                if (selectedDiffFile?.path != commitFile.path) return@invokeLater  // file switched
                inlineDiffPanel.showDiffRequest(request)
            }
        }
    }

    private fun buildDiffRequest(
        repoPath: String,
        commitFile: CommitFile,
        headBytes: ByteArray?,
        workBytes: ByteArray?,
        leftLabel: String = "HEAD",
        rightLabel: String = "Working Tree"
    ): SimpleDiffRequest {
        val factory = DiffContentFactory.getInstance()
        val filePath = VcsUtil.getFilePath(File(repoPath, commitFile.path).absolutePath, false)

        val headContent = if (headBytes != null)
            runCatching { factory.createFromBytes(project, headBytes, filePath) }.getOrElse { factory.createEmpty() }
        else
            factory.createEmpty()

        val workContent = if (workBytes != null)
            runCatching { factory.createFromBytes(project, workBytes, filePath) }.getOrElse { factory.createEmpty() }
        else
            factory.createEmpty()

        return SimpleDiffRequest(
            commitFile.path,
            headContent,
            workContent,
            leftLabel,
            rightLabel
        )
    }

    private fun doShowFullDiff() {
        val backend = selectedBackend ?: return
        val file = selectedDiffFile ?: return
        val commitHash = selectedCommitHash  // snapshot to avoid race on EDT
        val snapshotKey = System.identityHashCode(backend)

        ApplicationManager.getApplication().executeOnPooledThread {
            val request: SimpleDiffRequest = if (commitHash != null) {
                // History mode: compare file at parent commit vs file at selected commit
                val beforeBytes = backend.getFileAtRevision("$commitHash^", file.path)  // parent (before)
                val afterBytes  = backend.getFileAtRevision(commitHash, file.path)       // at commit (after)
                buildDiffRequest(backend.repoPath, file, beforeBytes, afterBytes,
                    leftLabel = "$commitHash^", rightLabel = commitHash.take(8))
            } else {
                // Staging mode: HEAD vs working tree (existing behaviour)
                val headBytes: ByteArray? = when (file.status) {
                    GitFileStatus.UNTRACKED, GitFileStatus.ADDED -> null
                    else -> backend.getHeadContent(file.path)
                }
                val workBytes: ByteArray? = when (file.status) {
                    GitFileStatus.DELETED -> null
                    else -> runCatching { File(backend.repoPath, file.path).readBytes() }.getOrNull()
                }
                buildDiffRequest(backend.repoPath, file, headBytes, workBytes)
            }

            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
                DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
            }
        }
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    override fun dispose() {
        disposed = true
        registry.removeListener(registryListener)
        ActiveBrowserTracker.getInstance(project).removeListener(navListener)
    }
}
