package ro.faur.explorer.quickopen.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import ro.faur.explorer.actions.FileActions
import ro.faur.explorer.model.Bookmark
import ro.faur.explorer.model.BookmarkManager
import ro.faur.explorer.quickopen.backend.MinusculeMatcherRanker
import ro.faur.explorer.quickopen.backend.NucleoNative
import ro.faur.explorer.quickopen.backend.RankerSelector
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
import ro.faur.explorer.settings.QuickOpenSettings
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuickOpenPanel(
    private val project: Project,
    private val currentPath: String,
    private val candidates: List<SearchCandidate>,
    private val initialQuery: String = "",
    private val onSelected: (SearchCandidate) -> Unit
) : JPanel(BorderLayout()), Disposable {

    private val textScorer = MinusculeMatcherRanker()
    private val ranker = Ranker(textScorer)
    private val candidatePool = CandidatePool(VfsEnumerator())
    private val panelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentRoot: String = currentPath

    companion object {
        private const val MAX_CONTENT_RESULTS = 200
    }

    val searchField = SearchTextField(true).apply {
        isFocusable = true
        minimumSize = Dimension(600, preferredSize.height)
    }

    private val listModel = CollectionListModel<SearchResult>()
    private val resultRenderer = SearchResultRenderer()
    val resultList = JBList(listModel).apply {
        cellRenderer = resultRenderer
        visibleRowCount = 12
        emptyText.setText("No results — try a different query")
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    private val countLabel = JBLabel("").apply {
        foreground = java.awt.Color.GRAY
        font = font.deriveFont(11f)
    }

    private val truncationLabel = JBLabel("").apply { isVisible = false }

    private val rankerStatusLabel = JBLabel("").apply {
        foreground = java.awt.Color.GRAY
        font = font.deriveFont(10f)
        border = javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        toolTipText = "Click to change search root"
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                openRootPicker()
            }
        })
    }

    private val previewPane = PreviewPane(project).apply { isVisible = false }
    private var previewVisible = false

    private val modeChipLabel = JBLabel("").apply {
        font = font.deriveFont(java.awt.Font.BOLD, 11f)
        foreground = java.awt.Color(0x6897BB)
    }

    private val hintLabel = JBLabel(
        "↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close"
    ).apply {
        font = font.deriveFont(10f)
        foreground = java.awt.Color.GRAY
        border = javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }

    // Speed-dial: top frecent paths from FrecencyStore (pure recency, no bookmark-first logic)
    private val speedDialItems: List<SearchCandidate> = run {
        val store = FrecencyStore.getInstance()
        val count = try { QuickOpenSettings.getInstance().state.speedDialCount } catch (_: Exception) { 6 }
        candidates
            .filter { it.type != CandidateType.ACTION }
            .sortedByDescending { store.recencyScore(it.fullPath) }
            .take(count)
    }

    private val speedDialPanel: SpeedDialPanel = SpeedDialPanel(
        items = speedDialItems,
        onActivated = { candidate ->
            selectedId = candidate.id
            FrecencyStore.getInstance().recordVisit(candidate.fullPath)
            onSelected(candidate)
            popup?.closeOk(null)
        },
        onEscapeUp = { searchField.requestFocus() },
    )

    private val cardLayout = CardLayout()
    private val contentCard = JPanel(cardLayout).apply {
        add(speedDialPanel, "speed-dial")
        add(JBScrollPane(resultList), "results")
    }

    private val searchScheduler = AppExecutorUtil.createBoundedScheduledExecutorService("QuickOpenSearch", 1)
    private var pendingSearch: ScheduledFuture<*>? = null
    private var selectedId: String? = null

    private val recentQueries = mutableListOf<String>()
    private var recentQueryIndex = -1

    var popup: JBPopup? = null  // set by QuickOpenPopup after creation

    init {
        val statusPanel = JPanel(java.awt.GridLayout(2, 1)).apply {
            val topRow = JPanel(BorderLayout()).apply {
                add(truncationLabel, BorderLayout.WEST)
                add(countLabel, BorderLayout.EAST)
                add(hintLabel, BorderLayout.CENTER)
            }
            val bottomRow = JPanel(BorderLayout()).apply {
                add(rankerStatusLabel, BorderLayout.CENTER)
            }
            add(topRow)
            add(bottomRow)
        }

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(contentCard, BorderLayout.CENTER)
        centerPanel.add(previewPane, BorderLayout.EAST)
        centerPanel.add(statusPanel, BorderLayout.SOUTH)

        val northPanel = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.CENTER)
            add(modeChipLabel, BorderLayout.EAST)
        }
        add(northPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        preferredSize = Dimension(700, 480)

        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleSearch()
            override fun removeUpdate(e: DocumentEvent) = scheduleSearch()
            override fun changedUpdate(e: DocumentEvent) = scheduleSearch()
        })

        // Down arrow from search field: focus speed-dial or result list depending on active card
        DumbAwareAction.create {
            if (searchField.text.isBlank() && !speedDialPanel.isEmpty()) {
                speedDialPanel.focusAndSelectFirst()
            } else {
                resultList.requestFocus()
                if (resultList.selectedIndex < 0 && listModel.size > 0) selectFirstNonHeader()
            }
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
            ), searchField
        )

        // Up arrow from search field: no-op (already at top), but needed to prevent beep
        DumbAwareAction.create {
            // Already at the search field — nothing to do
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)
            ), searchField
        )

        // Tab: toggle preview pane — registered on both searchField and resultList as
        // DumbAwareAction so IntelliJ's dispatcher fires before Swing focus traversal.
        // A KeyAdapter on resultList would never fire because Tab is a Swing focus-traversal
        // key and is consumed by KeyboardFocusManager before key listeners see it.
        val togglePreviewAction = DumbAwareAction.create { togglePreview() }
        listOf(searchField, resultList).forEach {
            togglePreviewAction.registerCustomShortcutSet(
                com.intellij.openapi.actionSystem.CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
                ), it
            )
        }

        // Shift+Tab on resultList: navigate to previous group
        DumbAwareAction.create { navigateToPrevGroup() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
            ), resultList
        )

        // Ctrl+R on search field: cycle recent queries
        DumbAwareAction.create { cycleRecentQuery() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)
            ), searchField
        )

        // Ctrl+P: move selection up (Emacs style)
        val upAction = DumbAwareAction.create {
            if (resultList.selectedIndex > 0) {
                resultList.selectedIndex = resultList.selectedIndex - 1
                resultList.ensureIndexIsVisible(resultList.selectedIndex)
            }
        }
        listOf(searchField, resultList).forEach {
            upAction.registerCustomShortcutSet(
                com.intellij.openapi.actionSystem.CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)
                ), it
            )
        }

        // Ctrl+N: move selection down (Emacs style)
        val downAction = DumbAwareAction.create {
            resultList.requestFocus()
            val next = resultList.selectedIndex + 1
            if (next < listModel.size) {
                resultList.selectedIndex = next
                resultList.ensureIndexIsVisible(next)
            }
        }
        listOf(searchField, resultList).forEach {
            downAction.registerCustomShortcutSet(
                com.intellij.openapi.actionSystem.CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)
                ), it
            )
        }

        // Cmd+N: create new file/folder
        val createAction = DumbAwareAction.create { createNewEntry() }
        listOf(searchField, resultList).forEach {
            createAction.registerCustomShortcutSet(
                com.intellij.openapi.actionSystem.CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.META_DOWN_MASK)
                ), it
            )
        }

        // Cmd+1–9: speed-dial when query blank, else activate Nth result
        // Registered on both searchField and resultList so digits don't type into the search bar
        for (digit in 1..9) {
            val vk = KeyEvent.VK_0 + digit
            val idx = digit - 1
            val digitAction = DumbAwareAction.create {
                if (searchField.text.isBlank() && !speedDialPanel.isEmpty()) {
                    speedDialPanel.activateAt(idx)
                } else {
                    activateAtIndex(idx)
                }
            }
            listOf(searchField, resultList).forEach { component ->
                digitAction.registerCustomShortcutSet(
                    com.intellij.openapi.actionSystem.CustomShortcutSet(
                        KeyStroke.getKeyStroke(vk, InputEvent.META_DOWN_MASK)
                    ), component
                )
            }
        }

        // Down arrow from search field: focus speed-dial or result list depending on card
        // (The existing VK_DOWN DumbAwareAction on searchField focuses resultList;
        //  we also need arrow-key navigation within the speed-dial panel when it is showing.)

        // ResultList key handler
        resultList.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    // Up at top: back to search field
                    e.keyCode == KeyEvent.VK_UP && resultList.selectedIndex == 0 ->
                        searchField.requestFocus()

                    // Cmd+Enter: open with sub-menu
                    e.keyCode == KeyEvent.VK_ENTER && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0 -> {
                        showOpenWithMenu(); e.consume()
                    }

                    // Alt+Enter: context mini-menu
                    e.keyCode == KeyEvent.VK_ENTER && (e.modifiersEx and InputEvent.ALT_DOWN_MASK) != 0 -> {
                        showContextMenu(); e.consume()
                    }

                    // Enter: activate selected (single or multi)
                    e.keyCode == KeyEvent.VK_ENTER ->
                        activateSelected()

                    // Cmd+D: toggle bookmark
                    e.keyCode == KeyEvent.VK_D && (e.modifiersEx and InputEvent.META_DOWN_MASK) != 0 -> {
                        toggleBookmark(); e.consume()
                    }

                    // /: refocus search field
                    e.keyCode == KeyEvent.VK_SLASH && e.modifiersEx == 0 -> {
                        searchField.requestFocus()
                        e.consume()
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

        // Cmd+Shift+C: copy relative path
        DumbAwareAction.create { copyRelativePath() }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
            ), resultList
        )

        // Cmd+[: pop one path segment from query
        val popSegmentAction = DumbAwareAction.create { popPathSegment() }
        listOf(searchField, resultList).forEach { component ->
            popSegmentAction.registerCustomShortcutSet(
                com.intellij.openapi.actionSystem.CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.META_DOWN_MASK)
                ), component
            )
        }

        // Start background enumeration; onUpdate fires on background thread — dispatch to EDT
        candidatePool.refreshAsync(currentPath) {
            ApplicationManager.getApplication().invokeLater({
                if (candidatePool.isTruncated) {
                    val cap = try { QuickOpenSettings.getInstance().state.maxIndexSize } catch (_: Exception) { 50_000 }
                    truncationLabel.text = "\u26a0 Index truncated at $cap files. Narrow your root."
                    truncationLabel.isVisible = true
                } else {
                    truncationLabel.isVisible = false
                }
                scheduleSearch()
                updateStatusBar()
            }, ModalityState.any())
        }

        // Pre-populate from editor cursor (Jump From Editor feature)
        if (initialQuery.isNotBlank()) {
            searchField.text = initialQuery
            ApplicationManager.getApplication().invokeLater({
                searchField.textEditor.selectAll()
            }, ModalityState.any())
        }

        scheduleSearch()
        updateStatusBar()
    }

    // ─── Status bar ───────────────────────────────────────────────────────────

    private fun updateStatusBar() {
        val rankerLabel = if (NucleoNative.isAvailable) "Nucleo"
                          else RankerSelector.active.name
        val rootDisplay = currentRoot.replace(System.getProperty("user.home"), "~")
        val count = candidatePool.getCandidates().size
        rankerStatusLabel.text = "Fuzzy: $rankerLabel · Root: $rootDisplay · ${"%,d".format(count)} files"
    }

    private fun openRootPicker() {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        descriptor.title = "Select Search Root"
        val chooser = FileChooserFactory.getInstance().createPathChooser(descriptor, project, this)
        chooser.choose(null) { files ->
            if (files.isNotEmpty()) {
                currentRoot = files.first().path
                updateStatusBar()
                candidatePool.refreshAsync(currentRoot) {
                    ApplicationManager.getApplication().invokeLater({
                        scheduleSearch()
                        updateStatusBar()
                    }, ModalityState.any())
                }
            }
        }
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
        pendingSearch?.cancel(true)   // true = interrupt if running
        val raw = searchField.text.trim()
        resultList.setPaintBusy(true)

        // Switch between speed-dial and results card
        if (raw.isBlank() && !speedDialPanel.isEmpty()) {
            cardLayout.show(contentCard, "speed-dial")
        } else {
            cardLayout.show(contentCard, "results")
        }

        // Update mode chip immediately (synchronous, no search needed)
        modeChipLabel.text = when (QueryParser.parse(raw).mode) {
            QueryMode.DIRS_ONLY -> "  [d:]  "
            QueryMode.FILES_ONLY -> "  [f:]  "
            QueryMode.BOOKMARKS_ONLY -> "  [b:]  "
            QueryMode.RECENT_ONLY -> "  [r:]  "
            QueryMode.COMMAND -> "  [>]  "
            QueryMode.REGEX -> "  [~]  "
            QueryMode.CONTENT_SEARCH -> "  [/:]  "
            QueryMode.UNIFIED -> ""
        }

        val isContentSearch = QueryParser.parse(raw).mode == QueryMode.CONTENT_SEARCH
        val delayMs = if (isContentSearch) 300L else 50L
        pendingSearch = searchScheduler.schedule({
            val results = performSearch(raw)
            ApplicationManager.getApplication().invokeLater({
                updateList(results, raw)
            }, ModalityState.any())
        }, delayMs, TimeUnit.MILLISECONDS)
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

        val limit = try { QuickOpenSettings.getInstance().state.maxDisplayedResults } catch (_: Exception) { 50 }
        val scored = ranker.rank(query.searchText, allCandidates, currentPath, limit = limit)
        return enrichWithGitStatus(scored.map { SearchResult(it) })
    }

    private fun performContentSearch(pattern: String): List<SearchResult> {
        if (pattern.isBlank()) return emptyList()
        val qs = try { QuickOpenSettings.getInstance().state } catch (_: Exception) { null }
        if (qs?.contentSearchEnabled == false) return emptyList()
        val searchScope = currentPath
        val rgPath = qs?.ripgrepPath?.ifBlank { "rg" } ?: "rg"
        return try {
            val matches = mutableListOf<ro.faur.explorer.quickopen.backend.ContentMatch>()
            val latch = CountDownLatch(1)
            panelScope.launch {
                try {
                    RipgrepContentSearch(rgPath, searchScope).search(pattern)
                        .take(MAX_CONTENT_RESULTS)
                        .collect { matches.add(it) }
                } finally { latch.countDown() }
            }
            latch.await(10, TimeUnit.SECONDS)
            matches.map { match ->
                SearchResult(ScoredCandidate(
                    SearchCandidate(
                        id = "content:${match.filePath}:${match.lineNumber}",
                        displayName = "${match.filePath.substringAfterLast('/')}:${match.lineNumber}",
                        fullPath = match.filePath,
                        parentPath = match.filePath.substringBeforeLast('/'),
                        type = CandidateType.CONTENT_MATCH,
                        contentSnippet = match.snippet,
                        contentMatchRanges = match.matchRanges.takeIf { it.isNotEmpty() }
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
        val limit = try { QuickOpenSettings.getInstance().state.maxDisplayedResults } catch (_: Exception) { 50 }
        return candidates
            .filter { regex.containsMatchIn(it.fullPath) }
            .take(limit)
            .map { SearchResult(ScoredCandidate(it, 1.0)) }
    }

    // ─── List update ──────────────────────────────────────────────────────────

    @RequiresEdt
    private fun updateList(results: List<SearchResult>, query: String) {
        resultRenderer.currentQuery = query
        val prevId = selectedId ?: resultList.selectedValue?.scored?.candidate?.id
        listModel.replaceAll(results)
        resultList.setPaintBusy(false)

        val realCount = results.count { !it.isGroupHeader }
        countLabel.text = if (realCount > 0) "$realCount results" else ""

        if (realCount == 0) {
            resultList.emptyText.setText(smartEmptyMessage(QueryParser.parse(query).mode))
        }

        if (prevId != null) {
            val idx = results.indexOfFirst { it.scored?.candidate?.id == prevId }
            if (idx >= 0) resultList.selectedIndex = idx
        }
        if (resultList.selectedIndex < 0 && listModel.size > 0) selectFirstNonHeader()

        // Track non-empty queries for Ctrl+R
        if (query.isNotBlank() && (recentQueries.isEmpty() || recentQueries.last() != query)) {
            recentQueries.add(query)
            val historyLimit = try { QuickOpenSettings.getInstance().state.recentQueryHistory } catch (_: Exception) { 20 }
            if (recentQueries.size > historyLimit) recentQueries.removeAt(0)
            recentQueryIndex = recentQueries.size
        }
    }

    private fun selectFirstNonHeader() {
        val idx = (0 until listModel.size).firstOrNull { !listModel.getElementAt(it).isGroupHeader } ?: 0
        resultList.selectedIndex = idx
    }

    // ─── Smart empty-state messages ───────────────────────────────────────────

    private fun smartEmptyMessage(mode: QueryMode): String = when (mode) {
        QueryMode.DIRS_ONLY      -> "No directories match — try without d: to search all types"
        QueryMode.FILES_ONLY     -> "No files match — try without f: to include directories"
        QueryMode.BOOKMARKS_ONLY -> "No bookmarks match — press ⌘D to bookmark a path first"
        QueryMode.RECENT_ONLY    -> "No recent paths match — navigate somewhere first"
        QueryMode.COMMAND        -> "No actions match — try typing 'toggle' or 'home'"
        QueryMode.REGEX          -> "No paths match that regex pattern"
        QueryMode.CONTENT_SEARCH -> "No files contain that pattern (rg not found or no matches)"
        QueryMode.UNIFIED        -> "No results — try a shorter query or ⌘[ to pop a segment"
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun openContentMatchAtLine(candidate: SearchCandidate) {
        val lineNumber = candidate.id.substringAfterLast(':').toIntOrNull() ?: 1
        val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath) ?: return
        NonProjectFileWritingAccessProvider.allowWriting(listOf(vf))
        OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)
        popup?.closeOk(null)
    }

    private fun activateSelected() {
        val selected = resultList.selectedValuesList
            .mapNotNull { it?.scored?.candidate }
            .filter { it.id.isNotBlank() }
        if (selected.isEmpty()) return
        selected.forEach { candidate ->
            selectedId = candidate.id
            FrecencyStore.getInstance().recordVisit(candidate.fullPath)
        }
        // Handle CONTENT_MATCH differently — open at exact line
        val contentMatches = selected.filter { it.type == CandidateType.CONTENT_MATCH }
        val otherSelected = selected.filter { it.type != CandidateType.CONTENT_MATCH }
        if (contentMatches.isNotEmpty()) {
            openContentMatchAtLine(contentMatches.first())
            return  // popup already closed in openContentMatchAtLine
        }
        otherSelected.forEach { onSelected(it) }
        popup?.closeOk(null)
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

    private fun copyRelativePath() {
        val absolutePath = resultList.selectedValue?.scored?.candidate?.fullPath ?: return
        val projectRoot = project.basePath ?: ""
        val relativePath = if (projectRoot.isNotBlank() && absolutePath.startsWith("$projectRoot/")) {
            absolutePath.removePrefix("$projectRoot/")
        } else {
            absolutePath
        }
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(relativePath), null)
    }

    private fun popPathSegment() {
        val current = searchField.text.trimEnd('/')
        if (current.isEmpty()) return
        val lastSlash = current.lastIndexOf('/')
        searchField.text = when {
            lastSlash < 0 -> ""
            lastSlash == 0 -> "/"
            else -> current.substring(0, lastSlash)
        }
    }

    // ─── Alt+Enter context mini-menu ──────────────────────────────────────────

    private fun showContextMenu() {
        val candidate = resultList.selectedValue?.scored?.candidate ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath)

        val items = buildList {
            if (vf != null) {
                add("Rename")
                add("Delete")
            }
            add("Copy Path")
            add("Copy Relative Path")
            val isBookmarked = BookmarkManager.getInstance().getBookmarks().any { it.path == candidate.fullPath }
            add(if (isBookmarked) "Remove Bookmark ⭐" else "Add Bookmark ⭐")
        }

        val step = object : BaseListPopupStep<String>("Actions for ${candidate.displayName}", items) {
            override fun onChosen(value: String, finalChoice: Boolean): PopupStep<*>? {
                when {
                    value == "Rename" && vf != null -> {
                        val newName = Messages.showInputDialog(project, "New name:", "Rename", null, vf.name, null)
                        if (!newName.isNullOrBlank()) {
                            FileActions.rename(vf, newName)
                            scheduleSearch()
                        }
                    }
                    value == "Delete" && vf != null -> {
                        val toTrash = ExplorerSettings.getInstance().state.deleteToTrash
                        val confirm = ExplorerSettings.getInstance().state.confirmDelete
                        if (!confirm || Messages.showYesNoDialog(project, "Delete ${vf.name}?", "Delete", null) == Messages.YES) {
                            FileActions.delete(vf, toTrash)
                            scheduleSearch()
                        }
                    }
                    value == "Copy Path" -> copySelectedPath()
                    value == "Copy Relative Path" -> copyRelativePath()
                    value.startsWith("Add Bookmark") || value.startsWith("Remove Bookmark") -> toggleBookmark()
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(resultList)
    }

    // ─── Cmd+N create file/folder ─────────────────────────────────────────────

    private fun createNewEntry() {
        val candidate = resultList.selectedValue?.scored?.candidate
        val dirPath = when {
            candidate == null -> currentPath
            java.io.File(candidate.fullPath).isDirectory -> candidate.fullPath
            else -> candidate.fullPath.substringBeforeLast('/')
        }
        val parentVf = LocalFileSystem.getInstance().findFileByPath(dirPath) ?: return

        val typeStep = object : BaseListPopupStep<String>("Create in $dirPath", listOf("New File", "New Folder")) {
            override fun onChosen(value: String, finalChoice: Boolean): PopupStep<*>? {
                val isFile = value == "New File"
                val name = Messages.showInputDialog(
                    project,
                    if (isFile) "File name:" else "Folder name:",
                    if (isFile) "New File" else "New Folder",
                    null, "", null
                ) ?: return PopupStep.FINAL_CHOICE
                if (name.isBlank()) return PopupStep.FINAL_CHOICE
                if (isFile) FileActions.createFile(parentVf, name)
                else FileActions.createFolder(parentVf, name)
                scheduleSearch()
                return PopupStep.FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(typeStep).showUnderneathOf(resultList)
    }

    // ─── Cmd+Enter open-with sub-menu ─────────────────────────────────────────

    private fun showOpenWithMenu() {
        val candidate = resultList.selectedValue?.scored?.candidate ?: return
        val isDir = java.io.File(candidate.fullPath).isDirectory

        val options = buildList {
            add("Navigate Explorer")
            if (!isDir) add("Open in Editor")
            add("Reveal in Finder")
            add("Open in Terminal")
        }

        val step = object : BaseListPopupStep<String>("Open '${candidate.displayName}' With…", options) {
            override fun onChosen(value: String, finalChoice: Boolean): PopupStep<*>? {
                when (value) {
                    "Navigate Explorer" -> {
                        onSelected(candidate)
                        popup?.closeOk(null)
                    }
                    "Open in Editor" -> {
                        val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath)
                        if (vf != null) {
                            NonProjectFileWritingAccessProvider.allowWriting(listOf(vf))
                            FileEditorManager.getInstance(project).openFile(vf, true)
                        }
                        popup?.closeOk(null)
                    }
                    "Reveal in Finder" -> revealInFinder()
                    "Open in Terminal" -> openInTerminal(candidate.fullPath)
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(resultList)
    }

    private fun openInTerminal(path: String) {
        try {
            val workDir = if (java.io.File(path).isDirectory) path
                          else path.substringBeforeLast('/')
            org.jetbrains.plugins.terminal.TerminalToolWindowManager
                .getInstance(project)
                .createLocalShellWidget(workDir, "Explorer")
        } catch (_: Exception) {}
    }

    override fun dispose() {
        previewPane.dispose()
        candidatePool.cancel()
        pendingSearch?.cancel(true)
        searchScheduler.shutdownNow()
        panelScope.cancel()
    }
}
