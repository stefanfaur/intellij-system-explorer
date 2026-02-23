package ro.faur.explorer.gitpanel.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
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

    private val registryListener: () -> Unit = { ApplicationManager.getApplication().invokeLater { rebuildCombo() } }

    private val navListener: (String?, String) -> Unit = { connName, path ->
        ApplicationManager.getApplication().invokeLater { selectMatchingBackend(connName, path) }
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
        ApplicationManager.getApplication().executeOnPooledThread {
            val branch = runCatching { backend.getCurrentBranch() }.getOrNull()
            val log    = runCatching { backend.getLog(200) }.getOrElse { emptyList() }
            val status = runCatching { backend.getWorkingTreeStatus() }.getOrElse { emptyList() }
            ApplicationManager.getApplication().invokeLater {
                currentBranch = branch
                logEntries    = log
                branchLabel.text = if (branch != null) "  \u2387 $branch  " else ""
                commitLogPanel.setData(log, status.size)
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
        if (entry == null) {
            // Working tree row
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val files = backend.getWorkingTreeStatus()
                    ApplicationManager.getApplication().invokeLater {
                        changedFilesPanel.setFiles(files, "Working Tree")
                        commitDetailsPanel.setCommit(null)
                    }
                } catch (_: Exception) {}
            }
        } else {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val files = backend.getCommitFiles(entry.hash)
                    val info  = backend.getCommitInfo(entry.hash)
                    ApplicationManager.getApplication().invokeLater {
                        changedFilesPanel.setFiles(files, "Changes in ${entry.hash.take(7)}")
                        commitDetailsPanel.setCommit(info)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    override fun dispose() {
        registry.removeListener(registryListener)
        ActiveBrowserTracker.getInstance(project).removeListener(navListener)
    }
}
