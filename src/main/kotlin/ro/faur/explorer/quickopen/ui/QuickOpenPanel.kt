package ro.faur.explorer.quickopen.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import ro.faur.explorer.model.Bookmark
import ro.faur.explorer.model.BookmarkManager
import ro.faur.explorer.quickopen.backend.MinusculeMatcherRanker
import ro.faur.explorer.quickopen.backend.RipgrepContentSearch
import ro.faur.explorer.quickopen.backend.VfsEnumerator
import ro.faur.explorer.quickopen.git.GitStatusProvider
import ro.faur.explorer.quickopen.index.CandidatePool
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.model.ScoredCandidate
import ro.faur.explorer.quickopen.query.QueryMode
import ro.faur.explorer.quickopen.query.QueryParser
import ro.faur.explorer.quickopen.query.RelativePathResolver
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import ro.faur.explorer.quickopen.ranking.Ranker
import ro.faur.explorer.settings.ExplorerSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.Future
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuickOpenPanel(
    private val project: Project,
    private val currentPath: String,
    private val candidates: List<SearchCandidate>,
    private val onSelected: (SearchCandidate) -> Unit
) : JPanel(BorderLayout()), Disposable {

    private val textScorer = MinusculeMatcherRanker()
    private val ranker = Ranker(textScorer)
    private val candidatePool = CandidatePool(VfsEnumerator())

    val searchField = SearchTextField(true).apply {
        isFocusable = true
        minimumSize = Dimension(600, preferredSize.height)
    }

    private val listModel = CollectionListModel<SearchResult>()
    val resultList = JBList(listModel).apply {
        cellRenderer = SearchResultRenderer()
        visibleRowCount = 12
        emptyText.setText("No results — try a different query")
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    private val countLabel = JBLabel("").apply {
        foreground = java.awt.Color.GRAY
        font = font.deriveFont(11f)
    }

    private val truncationLabel = JBLabel("⚠ Index truncated at 50,000 files. Narrow your root.").apply {
        isVisible = false
    }

    private val previewPane = PreviewPane().apply { isVisible = false }
    private var previewVisible = false

    private var pendingSearch: Future<*>? = null
    private var selectedId: String? = null

    private val recentQueries = mutableListOf<String>()
    private var recentQueryIndex = -1

    lateinit var popup: JBPopup  // set by QuickOpenPopup after creation

    init {
        val statusPanel = JPanel(BorderLayout()).apply {
            add(truncationLabel, BorderLayout.WEST)
            add(countLabel, BorderLayout.EAST)
        }

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(JBScrollPane(resultList), BorderLayout.CENTER)
        centerPanel.add(previewPane, BorderLayout.EAST)
        centerPanel.add(statusPanel, BorderLayout.SOUTH)

        add(searchField, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        preferredSize = Dimension(700, 480)

        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent) = scheduleSearch()
        })

        // Down arrow from search field: focus list
        DumbAwareAction.create {
            resultList.requestFocus()
            if (resultList.selectedIndex < 0 && listModel.size > 0) selectFirstNonHeader()
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
            ), searchField
        )

        // Tab on search field: toggle preview pane
        DumbAwareAction.create { togglePreview() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
            ), searchField
        )

        // Ctrl+R on search field: cycle recent queries
        DumbAwareAction.create { cycleRecentQuery() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)
            ), searchField
        )

        // ResultList key handler
        resultList.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    // Up at top: back to search field
                    e.keyCode == KeyEvent.VK_UP && resultList.selectedIndex == 0 ->
                        searchField.requestFocus()

                    // Cmd+Enter: reveal in Finder/Files
                    e.keyCode == KeyEvent.VK_ENTER && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0 -> {
                        revealInFinder(); e.consume()
                    }

                    // Enter: activate selected (single or multi)
                    e.keyCode == KeyEvent.VK_ENTER ->
                        activateSelected()

                    // Cmd+D: toggle bookmark
                    e.keyCode == KeyEvent.VK_D && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0 -> {
                        toggleBookmark(); e.consume()
                    }

                    // Tab: toggle preview pane
                    e.keyCode == KeyEvent.VK_TAB && e.modifiersEx == 0 -> {
                        togglePreview(); e.consume()
                    }

                    // Shift+Tab: navigate to previous group
                    e.keyCode == KeyEvent.VK_TAB && (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0 -> {
                        navigateToPrevGroup(); e.consume()
                    }

                    // Number keys 1-9: speed-dial activate Nth visible result
                    e.keyCode in KeyEvent.VK_1..KeyEvent.VK_9 && e.modifiersEx == 0 -> {
                        activateAtIndex(e.keyCode - KeyEvent.VK_1); e.consume()
                    }
                }
            }
        })

        // Mouse double-click: activate
        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) activateSelected()
            }
        })

        // Update preview on selection change (when visible)
        resultList.addListSelectionListener {
            if (!it.valueIsAdjusting && previewVisible) {
                val candidate = resultList.selectedValue?.scored?.candidate
                if (candidate != null) previewPane.update(candidate) else previewPane.clear()
            }
        }

        // Cmd+C: copy path
        DumbAwareAction.create { copySelectedPath() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK)
            ), resultList
        )

        // Start background enumeration; onUpdate fires on background thread — dispatch to EDT
        candidatePool.refreshAsync(currentPath) {
            ApplicationManager.getApplication().invokeLater({
                truncationLabel.isVisible = candidatePool.isTruncated
                scheduleSearch()
            }, ModalityState.any())
        }

        scheduleSearch()
    }

    // ─── Preview ──────────────────────────────────────────────────────────────

    private fun togglePreview() {
        previewVisible = !previewVisible
        previewPane.isVisible = previewVisible
        if (previewVisible) {
            val candidate = resultList.selectedValue?.scored?.candidate
            if (candidate != null) previewPane.update(candidate) else previewPane.clear()
        }
        revalidate(); repaint()
    }

    // ─── Recent query cycling (Ctrl+R) ────────────────────────────────────────

    private fun cycleRecentQuery() {
        if (recentQueries.isEmpty()) return
        recentQueryIndex = (recentQueryIndex + 1) % recentQueries.size
        searchField.text = recentQueries[recentQueryIndex]
    }

    // ─── Group navigation (Shift+Tab) ─────────────────────────────────────────

    private fun navigateToPrevGroup() {
        val size = listModel.size
        val current = resultList.selectedIndex.coerceAtLeast(0)
        for (i in current - 1 downTo 0) {
            if (listModel.getElementAt(i).isGroupHeader) {
                val nextItem = (i + 1 until size).firstOrNull { !listModel.getElementAt(it).isGroupHeader }
                if (nextItem != null) {
                    resultList.selectedIndex = nextItem
                    resultList.ensureIndexIsVisible(nextItem)
                    return
                }
            }
        }
    }

    // ─── Search scheduling ────────────────────────────────────────────────────

    private fun scheduleSearch() {
        pendingSearch?.cancel(false)
        val raw = searchField.text.trim()
        resultList.setPaintBusy(true)

        pendingSearch = AppExecutorUtil.getAppExecutorService().submit {
            val results = performSearch(raw)
            ApplicationManager.getApplication().invokeLater({
                updateList(results, raw)
            }, ModalityState.any())
        }
    }

    // ─── Search logic ─────────────────────────────────────────────────────────

    private fun performSearch(raw: String): List<SearchResult> {
        val query = QueryParser.parse(raw)

        // Resolve relative paths and alias expansion
        val resolvedPath = RelativePathResolver.resolve(query.searchText, currentPath)
            ?: RelativePathResolver.expandAliases(query.searchText)

        if (resolvedPath.startsWith("/") && java.io.File(resolvedPath).exists()) {
            return listOf(SearchResult(ScoredCandidate(
                SearchCandidate(
                    "direct:$resolvedPath",
                    resolvedPath.substringAfterLast('/'),
                    resolvedPath,
                    resolvedPath.substringBeforeLast('/'),
                    CandidateType.DIRECTORY
                ),
                score = 999.0
            )))
        }

        // Content search mode: delegate to ripgrep
        if (query.mode == QueryMode.CONTENT_SEARCH) {
            return performContentSearch(query.searchText)
        }

        val allCandidates = (candidates + candidatePool.getCandidates())
            .filter { matchesMode(it, query.mode) }
            .filter { matchesExtFilter(it, query.extensionFilter) }
            .filter { matchesScopeFilter(it, query.scopeFilter) }

        if (query.mode == QueryMode.REGEX) {
            return performRegexSearch(query.searchText, allCandidates)
        }

        val scored = ranker.rank(query.searchText, allCandidates, currentPath, limit = 50)
        return enrichWithGitStatus(scored.map { SearchResult(it) })
    }

    private fun performContentSearch(pattern: String): List<SearchResult> {
        if (pattern.isBlank()) return emptyList()
        val settings = ExplorerSettings.getInstance()
        if (!settings.state.contentSearchEnabled) return emptyList()
        val scope = settings.state.contentSearchScope.ifBlank { currentPath }
        val rgPath = settings.state.ripgrepPath.ifBlank { "rg" }
        return try {
            val matches = runBlocking {
                RipgrepContentSearch(rgPath, scope).search(pattern).toList()
            }
            matches.map { match ->
                SearchResult(ScoredCandidate(
                    SearchCandidate(
                        id = "content:${match.filePath}:${match.lineNumber}",
                        displayName = "${match.filePath.substringAfterLast('/')}:${match.lineNumber}",
                        fullPath = match.filePath,
                        parentPath = match.filePath.substringBeforeLast('/'),
                        type = CandidateType.CONTENT_MATCH,
                        contentSnippet = match.snippet
                    ),
                    score = 1.0
                ))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun enrichWithGitStatus(results: List<SearchResult>): List<SearchResult> {
        return results.map { r ->
            val candidate = r.scored?.candidate ?: return@map r
            // Skip actions — they have no filesystem path
            if (candidate.type == CandidateType.ACTION) return@map r
            val status = try {
                ReadAction.compute<String, Exception> {
                    GitStatusProvider.getStatus(project, candidate.fullPath)
                }
            } catch (_: Exception) { "" }
            if (status.isEmpty()) r else r.copy(gitStatus = status)
        }
    }

    private fun matchesMode(c: SearchCandidate, mode: QueryMode) = when (mode) {
        QueryMode.DIRS_ONLY -> c.type == CandidateType.DIRECTORY || c.type == CandidateType.BOOKMARK || c.type == CandidateType.RECENT
        QueryMode.FILES_ONLY -> c.type == CandidateType.FILE || c.type == CandidateType.OPEN_EDITOR
        QueryMode.BOOKMARKS_ONLY -> c.type == CandidateType.BOOKMARK
        QueryMode.RECENT_ONLY -> c.type == CandidateType.RECENT
        QueryMode.COMMAND -> c.type == CandidateType.ACTION
        else -> true
    }

    private fun matchesExtFilter(c: SearchCandidate, ext: String?) =
        ext == null || c.fullPath.endsWith(".$ext", ignoreCase = true)

    private fun matchesScopeFilter(c: SearchCandidate, scope: String?) =
        scope == null || c.fullPath.contains(scope, ignoreCase = true)

    private fun performRegexSearch(pattern: String, candidates: List<SearchCandidate>): List<SearchResult> {
        val regex = try { Regex(pattern, RegexOption.IGNORE_CASE) } catch (_: Exception) { return emptyList() }
        return candidates
            .filter { regex.containsMatchIn(it.fullPath) }
            .take(50)
            .map { SearchResult(ScoredCandidate(it, 1.0)) }
    }

    // ─── List update ──────────────────────────────────────────────────────────

    @RequiresEdt
    private fun updateList(results: List<SearchResult>, query: String) {
        val prevId = selectedId ?: resultList.selectedValue?.scored?.candidate?.id
        listModel.replaceAll(results)
        resultList.setPaintBusy(false)

        val realCount = results.count { !it.isGroupHeader }
        countLabel.text = if (realCount > 0) "$realCount results" else ""

        if (prevId != null) {
            val idx = results.indexOfFirst { it.scored?.candidate?.id == prevId }
            if (idx >= 0) resultList.selectedIndex = idx
        }
        if (resultList.selectedIndex < 0 && listModel.size > 0) selectFirstNonHeader()

        // Track non-empty queries for Ctrl+R
        if (query.isNotBlank() && (recentQueries.isEmpty() || recentQueries.last() != query)) {
            recentQueries.add(query)
            if (recentQueries.size > 20) recentQueries.removeAt(0)
            recentQueryIndex = recentQueries.size
        }
    }

    private fun selectFirstNonHeader() {
        val idx = (0 until listModel.size).firstOrNull { !listModel.getElementAt(it).isGroupHeader } ?: 0
        resultList.selectedIndex = idx
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun activateSelected() {
        val selected = resultList.selectedValuesList
            .mapNotNull { it?.scored?.candidate }
            .filter { it.id.isNotBlank() }
        if (selected.isEmpty()) return
        selected.forEach { candidate ->
            selectedId = candidate.id
            FrecencyStore.getInstance().recordVisit(candidate.fullPath)
        }
        selected.forEach { onSelected(it) }
        popup.closeOk(null)
    }

    private fun activateAtIndex(index: Int) {
        var realCount = 0
        for (i in 0 until listModel.size) {
            val item = listModel.getElementAt(i)
            if (item.isGroupHeader) continue
            if (realCount == index) {
                resultList.selectedIndex = i
                activateSelected()
                return
            }
            realCount++
        }
    }

    private fun revealInFinder() {
        val path = resultList.selectedValue?.scored?.candidate?.fullPath ?: return
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(arrayOf("open", "-R", path))
            } else {
                Runtime.getRuntime().exec(arrayOf("xdg-open", java.io.File(path).parent ?: path))
            }
        } catch (_: Exception) {}
    }

    private fun toggleBookmark() {
        val candidate = resultList.selectedValue?.scored?.candidate ?: return
        val bm = BookmarkManager.getInstance()
        val isBookmarked = bm.getBookmarks().any { it.path == candidate.fullPath }
        if (isBookmarked) {
            bm.removeBookmark(candidate.fullPath)
        } else {
            bm.addBookmark(Bookmark(candidate.displayName, candidate.fullPath))
        }
        scheduleSearch()
    }

    private fun copySelectedPath() {
        val path = resultList.selectedValue?.scored?.candidate?.fullPath ?: return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(path), null)
    }

    override fun dispose() {
        candidatePool.cancel()
        pendingSearch?.cancel(true)
    }
}
