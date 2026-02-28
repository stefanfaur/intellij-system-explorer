# Architecture

**Analysis Date:** 2026-02-28

## Pattern Overview

**Overall:** IntelliJ Platform Plugin — event-driven, service-oriented, Swing UI layered on IntelliJ Platform SDK

**Key Characteristics:**
- Two registered tool windows: `System Explorer` (right anchor) and `System Explorer Git` (left anchor)
- IntelliJ Platform services (application-scoped and project-scoped) hold persistent state via `PersistentStateComponent`
- Abstract `BrowserPanel` base class drives a polymorphic panel model (local vs remote) behind a `BrowserHost` tab manager
- Actions registered in `plugin.xml` dispatch to UI components via `ExplorerActionUtil` lookup
- Remote file I/O is entirely async (background thread + `SwingUtilities.invokeLater`) to avoid EDT blocking

## Layers

**Plugin Registration Layer:**
- Purpose: Declare services, actions, listeners, tool windows, and configurables to the IntelliJ Platform
- Location: `src/main/resources/META-INF/plugin.xml`
- Contains: Extension points, action registrations, keyboard shortcuts, settings hierarchy
- Depends on: Nothing (declarative XML)
- Used by: IntelliJ Platform at startup

**Tool Window Factory Layer:**
- Purpose: Bootstrap UI panels when IntelliJ creates tool window content
- Location: `src/main/kotlin/ro/faur/explorer/ExplorerToolWindowFactory.kt`, `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelToolWindowFactory.kt`
- Contains: `ToolWindowFactory` implementations
- Depends on: UI layer, IntelliJ `ToolWindow` API
- Used by: IntelliJ Platform when opening tool windows

**UI Layer:**
- Purpose: Swing-based panel components that manage user interaction, navigation, and display
- Location: `src/main/kotlin/ro/faur/explorer/ui/`, `src/main/kotlin/ro/faur/explorer/remote/ui/`, `src/main/kotlin/ro/faur/explorer/gitpanel/ui/`, `src/main/kotlin/ro/faur/explorer/quickopen/ui/`
- Contains: `ExplorerPanel`, `BrowserHost`, `BrowserPanel` (abstract), `LocalBrowserPanel`, `RemoteBrowserPanel`, `GitPanelComponent`, `QuickOpenPanel`, dialog classes
- Depends on: Actions layer, Model layer, Settings layer, Remote layer
- Used by: Tool window factories

**Actions Layer:**
- Purpose: IntelliJ `AnAction` implementations for keyboard shortcuts and menu items; dispatch to UI components
- Location: `src/main/kotlin/ro/faur/explorer/actions/`
- Contains: `ExplorerActions.kt` (toggle, open, back, copy/cut/paste, rename, delete, refresh), `FileActions.kt`, `NavigationActions.kt`, `QuickOpenAction.kt`, `SwitchPanelAction.kt`, `DragDropHandler.kt`, `FileTreeTransferHandler.kt`
- Depends on: Model layer, Settings layer, UI components (found via `ExplorerActionUtil`)
- Used by: IntelliJ Platform (action system), UI layer

**Model Layer:**
- Purpose: Domain data structures and local file system model
- Location: `src/main/kotlin/ro/faur/explorer/model/`
- Contains: `FileEntry.kt`, `FileTreeModel.kt` (VFS-backed), `Bookmark.kt`, `BookmarkManager.kt` (persistent state), `FileComparator.kt`
- Depends on: IntelliJ VFS API, `FrecencyStore`
- Used by: UI layer, Actions layer

**Remote Layer:**
- Purpose: SFTP connection management, remote file operations, and SSH infrastructure
- Location: `src/main/kotlin/ro/faur/explorer/remote/`
- Contains: `SftpFileOperations.kt`, `SftpFileTreeModel.kt`, `SftpEntry.kt`, `ConnectionProfile.kt`, `ConnectionState.kt`, `CrossPanelTransferService.kt`, `DirectoryCache.kt`, `SshConfigParser.kt`, `SshTerminalAction.kt`, `TempFileCleanupListener.kt`, `FileSizeLimitChecker.kt`, sub-packages: `git/`, `security/`, `settings/`, `ui/`
- Depends on: Apache MINA SSHD client, IntelliJ Credential Store, Settings layer
- Used by: UI layer (`RemoteBrowserPanel`), Git panel layer

**Remote Git Layer:**
- Purpose: VCS integration for remote repositories over SSH
- Location: `src/main/kotlin/ro/faur/explorer/remote/git/`
- Contains: `RemoteGitVcs.kt`, `RemoteGitVcsManager.kt`, `RemoteGitCommandExecutor.kt`, `RemoteGitStatusCache.kt`, `RemoteGitTreeDecorator.kt`, `RemoteGitAnnotationProvider.kt`, `RemoteGitHistoryProvider.kt`, `RemoteGitDiffProvider.kt`, `RemoteGitChangeProvider.kt`, `RemoteGitBranchWidgetFactory.kt`, `GitStatusParser.kt`, `GitLogParser.kt`, `GitBlameParser.kt`
- Depends on: Remote layer (SSH execution), IntelliJ VCS API
- Used by: UI decoration, Git panel, IntelliJ VCS framework

**Git Panel Layer:**
- Purpose: Standalone "System Explorer Git" tool window for commit browsing and basic git operations on local and remote repos
- Location: `src/main/kotlin/ro/faur/explorer/gitpanel/`
- Contains: `GitBackend.kt` (interface), `LocalGitBackend.kt`, `RemoteGitBackend.kt`, `GitRepositoryRegistry.kt`, `ActiveBrowserTracker.kt`, `exec/` (command executors), `ui/` (panels)
- Depends on: Remote layer (for remote backend), IntelliJ Git4Idea (for local backend)
- Used by: `GitPanelToolWindowFactory`

**Quick Open Layer:**
- Purpose: Fuzzy file search popup with frecency ranking, content search, and query language
- Location: `src/main/kotlin/ro/faur/explorer/quickopen/`
- Contains: `backend/` (ranker backends: Nucleo native, MinusculeMatcherRanker, FallbackRanker, RipgrepContentSearch, enumerators), `index/CandidatePool.kt`, `model/` (SearchCandidate, ScoredCandidate, CandidateType, UsageSignals, ActionCandidate), `query/QueryParser.kt`, `query/RelativePathResolver.kt`, `ranking/FrecencyStore.kt`, `ranking/Ranker.kt`, `aliases/TeleportAliasStore.kt`, `git/GitStatusProvider.kt`, `ui/` (popup, panel, result renderer, preview pane, speed dial)
- Depends on: VFS API, FrecencyStore (persistent), BookmarkManager, native Nucleo library (JNI), ripgrep binary
- Used by: `QuickOpenAction`, UI layer

**Settings Layer:**
- Purpose: Persistent configuration using IntelliJ `PersistentStateComponent`
- Location: `src/main/kotlin/ro/faur/explorer/settings/`, `src/main/kotlin/ro/faur/explorer/remote/settings/`
- Contains: `ExplorerSettings.kt` (local browser prefs), `QuickOpenSettings.kt`, `SystemExplorerConfigurable.kt`, `ExplorerConfigurable.kt`, `QuickOpenConfigurable.kt`, `RemoteExplorerSettings.kt`, `RemoteConnectionSettings.kt` (project-scoped connection profiles)
- Depends on: IntelliJ Settings API
- Used by: All layers

**Utilities:**
- Purpose: Shared helper functions
- Location: `src/main/kotlin/ro/faur/explorer/util/`
- Contains: `FileSizeFormatter.kt`, `GlobFilter.kt`, `PathUtils.kt`
- Depends on: Nothing (pure utilities)
- Used by: Model, Remote, UI layers

## Data Flow

**Local File Navigation:**
1. User action (keyboard shortcut, button click) triggers `AnAction` or button listener in `ExplorerPanel`
2. `ExplorerPanel` delegates to `BrowserHost.activePanel`
3. `LocalBrowserPanel.doNavigateTo(path)` calls IntelliJ VFS to resolve `VirtualFile`
4. `FileTreeModel.getChildren()` filters and sorts VFS children
5. `FileTreeComponent` (JTree) renders tree nodes via `ColoredTreeCellRenderer`
6. Status bar updates via `updateStatus()`

**Remote SFTP Navigation:**
1. User selects a saved `ConnectionProfile` from connect dropdown in `ExplorerPanel`
2. Background thread calls `SftpFileOperations.create(profile, ...)` via Apache MINA SSHD
3. On success (EDT): `RemoteBrowserPanel` created and added to `BrowserHost` as a new tab
4. `RemoteBrowserPanel.doNavigateTo(path)` calls `SftpFileOperations.listDirectory()`
5. `DirectoryCache` caches listing results to reduce SFTP round trips
6. UI refreshes tree via `SftpFileTreeModel`

**Quick Open Search:**
1. `QuickOpenAction` (Cmd+Shift+P) opens `QuickOpenPopup`
2. `QuickOpenPanel` creates `CandidatePool` with candidates from `VfsEnumerator` or `RipgrepEnumerator`
3. User query parsed by `QueryParser` → determines `QueryMode` (fuzzy, content, relative, etc.)
4. `Ranker` selects `RankerBackend` via `RankerSelector` (Nucleo native → MinusculeMatcher → Fallback)
5. `FrecencyStore.getSignals()` provides recency/frequency boosts applied to scores
6. Results rendered in `JBList` via `SearchResultRenderer`; preview shown in `PreviewPane`
7. On selection: navigates `ExplorerPanel` to chosen path and records visit in `FrecencyStore`

**Cross-Panel Drag and Drop (Local → Remote):**
1. `FileTreeTransferHandler` exports `Transferable` via Swing DnD (`dragEnabled = true`)
2. `RemoteTreeDropTarget` (implements `DnDNativeTarget`) receives drop
3. `CrossPanelTransferService.uploadAsync()` runs on Dispatchers.IO, uploads recursively via SFTP
4. `DirectoryCache` is invalidated for target directory; remote panel refreshes

**State Management:**
- Application-scoped services (singletons): `ExplorerSettings`, `QuickOpenSettings`, `BookmarkManager`, `FrecencyStore`, `TeleportAliasStore`, `RemoteExplorerSettings`, `RemoteGitSettings`
- Project-scoped services: `RemoteConnectionSettings` (connection profiles), `RemoteBookmarkManager`, `RemoteGitVcsManager`, `GitRepositoryRegistry`, `ActiveBrowserTracker`
- All services persist via IntelliJ XML serialization to `~/.config/JetBrains/<IDE>/options/`

## Key Abstractions

**BrowserPanel (abstract):**
- Purpose: Common interface for all file browser panels (local and remote)
- Examples: `src/main/kotlin/ro/faur/explorer/ui/LocalBrowserPanel.kt`, `src/main/kotlin/ro/faur/explorer/remote/ui/RemoteBrowserPanel.kt`
- Pattern: Abstract class with template method — `assemblePanelUI()` must be called in subclass init; `doNavigateTo()` is the extension point

**GitBackend (interface):**
- Purpose: Abstracts local vs remote git operations for the Git panel
- Examples: `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt`, `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt`
- Pattern: Strategy pattern — `GitRepositoryRegistry` holds multiple backends; `ActiveBrowserTracker` selects active one

**RankerBackend (interface):**
- Purpose: Pluggable fuzzy ranking algorithm for Quick Open
- Examples: `src/main/kotlin/ro/faur/explorer/quickopen/backend/NucleoRanker.kt`, `src/main/kotlin/ro/faur/explorer/quickopen/backend/MinusculeMatcherRanker.kt`, `src/main/kotlin/ro/faur/explorer/quickopen/backend/FallbackRanker.kt`
- Pattern: Strategy with capability check — `RankerSelector.select()` picks the best available backend (Nucleo → IntelliJ native → fallback)

**PersistentStateComponent services:**
- Purpose: Settings and data that survive IDE restarts
- Examples: `ExplorerSettings`, `BookmarkManager`, `FrecencyStore`, `RemoteConnectionSettings`
- Pattern: Standard IntelliJ `@State` + `@Storage` annotations; accessed via companion `getInstance()` singleton

## Entry Points

**System Explorer Tool Window:**
- Location: `src/main/kotlin/ro/faur/explorer/ExplorerToolWindowFactory.kt`
- Triggers: IntelliJ loads tool window on first activation
- Responsibilities: Instantiates `ExplorerPanel`, registers it with IntelliJ disposable chain

**Git Panel Tool Window:**
- Location: `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelToolWindowFactory.kt`
- Triggers: IntelliJ loads "System Explorer Git" tool window
- Responsibilities: Instantiates `GitPanelComponent`, registers disposable

**Quick Open Action:**
- Location: `src/main/kotlin/ro/faur/explorer/actions/QuickOpenAction.kt`
- Triggers: Cmd+Shift+P keyboard shortcut (global)
- Responsibilities: Creates `QuickOpenPopup`, ties it to current project context

**Toggle Explorer Action:**
- Location: `src/main/kotlin/ro/faur/explorer/actions/ExplorerActions.kt` (`ToggleExplorerAction`)
- Triggers: Alt+E (Win/Linux) or Cmd+Alt+E (Mac) keyboard shortcut
- Responsibilities: Show/hide/focus System Explorer tool window

**Application Lifecycle Listener:**
- Location: `src/main/kotlin/ro/faur/explorer/remote/TempFileCleanupListener.kt`
- Triggers: IDE shutdown (`AppLifecycleListener`)
- Responsibilities: Cleans up temp files downloaded from remote for editor viewing

**Editor Save Listener:**
- Location: `src/main/kotlin/ro/faur/explorer/remote/RemoteEditorSaveListener.kt`
- Triggers: `FileDocumentManagerListener` (before/after save)
- Responsibilities: Re-uploads modified temp files back to remote SFTP server

## Error Handling

**Strategy:** Recoverable errors shown as IntelliJ balloon notifications; connection failures shown in UI with retry option

**Patterns:**
- SFTP connection errors: Caught in `ExplorerPanel.connectToRemote()`, shown via `NotificationGroupManager` balloon (`SftpBrowser.Notifications` group)
- Batch transfer errors: `CrossPanelTransferService` collects per-file failures into `BatchTransferException`; all errors reported together after batch completes
- Quick Open backend unavailability: `RankerSelector` degrades gracefully to next available backend; no user-visible error
- Git command failures: `GitCommandResult` sealed class carries stdout/stderr; UI panels display error text inline

## Cross-Cutting Concerns

**Logging:** IntelliJ `Logger.getInstance(ClassName::class.java)` — used in `ExplorerPanel` and other key classes; not uniformly applied everywhere

**Validation:** Input validation inline in action/dialog code; path existence checked via VFS before navigation

**Authentication:** Remote credentials managed by `ro.faur.explorer.remote.security.CredentialHandler` using IntelliJ's native credential store; passwords never stored in plain text in settings XML

**Threading:** All SFTP I/O on background threads (via `Executors.newSingleThreadExecutor()` or `Dispatchers.IO`); UI updates dispatched back via `SwingUtilities.invokeLater()`; EDT-required methods annotated with `@RequiresEdt`

---

*Architecture analysis: 2026-02-28*
