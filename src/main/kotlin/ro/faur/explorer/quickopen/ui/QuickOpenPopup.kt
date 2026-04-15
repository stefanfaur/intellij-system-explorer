package ro.faur.explorer.quickopen.ui

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFocusManager
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.quickopen.query.RelativePathResolver
import ro.faur.explorer.model.BookmarkManager
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.ui.ExplorerPanel
import java.awt.Dimension

object QuickOpenPopup {

    fun show(project: Project, panel: ExplorerPanel, initialQuery: String = "") {
        val effectiveQuery = initialQuery.ifBlank { tryReadClipboardPath() }
        val currentPath = panel.currentPath
        val candidates = buildCandidates(project, panel)

        // Get remote connection details if connected to remote
        val connectionDetails = if (panel.isActiveRemote) {
            val remotePanel = panel.browserHost.activePanel as? ro.faur.explorer.remote.ui.RemoteBrowserPanel
            val manager = remotePanel?.connectionManager
            val name = remotePanel?.getConnectionName()
            if (manager != null && name != null && manager.isConnected(name)) {
                name to manager
            } else null
        } else null

        val qoPanel = QuickOpenPanel(
            project = project,
            currentPath = currentPath,
            candidates = candidates,
            initialQuery = effectiveQuery,
            isRemote = panel.isActiveRemote,
            onSelected = { candidate ->
                when (candidate.type) {
                    CandidateType.DIRECTORY, CandidateType.BOOKMARK, CandidateType.RECENT ->
                        panel.navigateTo(candidate.fullPath)
                    CandidateType.FILE, CandidateType.OPEN_EDITOR ->
                        openFile(project, candidate.fullPath)
                    CandidateType.ACTION -> {
                        @Suppress("UNCHECKED_CAST")
                        (candidate.extra["handler"] as? () -> Unit)?.invoke()
                    }
                    else -> panel.navigateTo(candidate.fullPath)
                }
            },
            sftpConnectionManager = connectionDetails?.second,
            connectionName = connectionDetails?.first
        )

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(qoPanel, qoPanel.searchField)
            .setProject(project)
            .setTitle("Quick Open")
            .setRequestFocus(true)
            .setResizable(true)
            .setMovable(true)
            .setDimensionServiceKey(project, "SystemExplorer.QuickOpenV2", true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .setMinSize(Dimension(600, 400))
            .createPopup()

        qoPanel.popup = popup

        popup.showCenteredInCurrentWindow(project)
        IdeFocusManager.getInstance(project).requestFocus(qoPanel.searchField, true)
    }

    private fun tryReadClipboardPath(): String {
        return try {
            val contents = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                .getContents(null) ?: return ""
            val text = (contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String)
                ?.trim() ?: return ""
            if ((text.startsWith("/") || text.startsWith("~")) && java.io.File(
                    RelativePathResolver.expandAliases(text)
                ).exists()) text else ""
        } catch (_: Exception) { "" }
    }

    private fun buildCandidates(project: Project, panel: ExplorerPanel): List<SearchCandidate> {
        val result = mutableListOf<SearchCandidate>()
        val store = FrecencyStore.getInstance()

        // Bookmarks
        try {
            BookmarkManager.getInstance().getBookmarks().forEach { bm ->
                result.add(SearchCandidate(
                    id = "bm:${bm.path}",
                    displayName = bm.name.ifBlank { bm.path.substringAfterLast('/') },
                    fullPath = bm.path,
                    parentPath = bm.path.substringBeforeLast('/', ""),
                    type = CandidateType.BOOKMARK,
                    signals = store.getSignals(bm.path).copy(isBookmarked = true)
                ))
            }
        } catch (_: Exception) {}

        // Recent paths from navigation history
        panel.getNavigationHistory().getRecentPaths(limit = 20).forEach { path ->
            if (result.none { it.fullPath == path }) {
                result.add(SearchCandidate(
                    id = "recent:$path",
                    displayName = path.substringAfterLast('/'),
                    fullPath = path,
                    parentPath = path.substringBeforeLast('/', ""),
                    type = CandidateType.RECENT,
                    signals = store.getSignals(path)
                ))
            }
        }

        // Open editors
        try {
            FileEditorManager.getInstance(project).openFiles.forEach { vf ->
                result.add(SearchCandidate(
                    id = "editor:${vf.path}",
                    displayName = vf.name,
                    fullPath = vf.path,
                    parentPath = vf.parent?.path ?: "",
                    type = CandidateType.OPEN_EDITOR,
                    signals = store.getSignals(vf.path).copy(isOpenInEditor = true)
                ))
            }
        } catch (_: Exception) {}

        // Explorer actions
        result.addAll(buildActionCandidates(project, panel))

        return result
    }

    private fun buildActionCandidates(project: Project, panel: ExplorerPanel): List<SearchCandidate> {
        val candidates = mutableListOf<SearchCandidate>()

        // Existing actions
        candidates += SearchCandidate(
            id = "action:toggle-hidden",
            displayName = "Toggle Hidden Files",
            fullPath = "action:toggle-hidden",
            parentPath = "",
            type = CandidateType.ACTION,
            extra = mapOf("handler" to ({ panel.toggleHiddenFiles() } as () -> Unit))
        )
        candidates += SearchCandidate(
            id = "action:go-home",
            displayName = "Go Home",
            fullPath = "action:go-home",
            parentPath = "",
            type = CandidateType.ACTION,
            extra = mapOf("handler" to ({ panel.navigateTo(NavigationActions.goHome(project)) } as () -> Unit))
        )
        candidates += SearchCandidate(
            id = "action:go-root",
            displayName = "Go to Root /",
            fullPath = "action:go-root",
            parentPath = "",
            type = CandidateType.ACTION,
            extra = mapOf("handler" to ({ panel.navigateTo("/") } as () -> Unit))
        )

        // Remote: one connect candidate per saved profile
        try {
            val profiles = ro.faur.explorer.remote.settings.RemoteConnectionSettings
                .getInstance(project).state.connections
            profiles.forEach { profile ->
                candidates += SearchCandidate(
                    id = "action:connect:${profile.name}",
                    displayName = "Connect to ${profile.name}",
                    fullPath = "action:connect:${profile.name}",
                    parentPath = "",
                    type = CandidateType.ACTION,
                    extra = mapOf("handler" to ({ panel.connectToRemote(profile) } as () -> Unit))
                )
            }
        } catch (_: Exception) {}

        // Remote: new connection
        candidates += SearchCandidate(
            id = "action:new-remote-connection",
            displayName = "New Remote Connection",
            fullPath = "action:new-remote-connection",
            parentPath = "",
            type = CandidateType.ACTION,
            extra = mapOf("handler" to ({
                val dialog = ro.faur.explorer.remote.ui.ConnectionDialog(project)
                if (dialog.showAndGet()) {
                    val prof = dialog.getProfile()
                    val pw = dialog.getPassword()?.let { String(it) }
                    if (dialog.shouldRememberPassword() && pw != null) {
                        ro.faur.explorer.remote.security.CredentialHandler
                            .storePassword(prof.name, prof.username, pw)
                    }
                    ro.faur.explorer.remote.settings.RemoteConnectionSettings
                        .getInstance(project).addConnection(prof)
                    panel.connectToRemote(prof, pw)
                }
            } as () -> Unit))
        )

        // Remote: disconnect (only when connected)
        if (panel.isRemotePanelVisible) {
            val activeName = panel.getActiveConnectionName() ?: "remote"
            candidates += SearchCandidate(
                id = "action:disconnect",
                displayName = "Disconnect from $activeName",
                fullPath = "action:disconnect",
                parentPath = "",
                type = CandidateType.ACTION,
                extra = mapOf("handler" to ({ panel.disconnectRemote() } as () -> Unit))
            )
        }

        return candidates
    }

    private fun openFile(project: Project, path: String) {
        val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path) ?: return
        NonProjectFileWritingAccessProvider.allowWriting(listOf(vf))
        FileEditorManager.getInstance(project).openFile(vf, true)
    }
}
