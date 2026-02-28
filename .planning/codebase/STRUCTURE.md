# Codebase Structure

**Analysis Date:** 2026-02-28

## Directory Layout

```
intellij-explorer/
├── src/
│   ├── main/
│   │   ├── kotlin/ro/faur/explorer/       # All plugin source code
│   │   │   ├── ExplorerToolWindowFactory.kt  # System Explorer tool window entry point
│   │   │   ├── actions/                   # IntelliJ AnAction implementations
│   │   │   ├── gitpanel/                  # Git panel tool window
│   │   │   │   ├── exec/                  # Git command execution layer
│   │   │   │   └── ui/                    # Git panel Swing components
│   │   │   ├── model/                     # Local filesystem domain model
│   │   │   ├── quickopen/                 # Quick Open fuzzy search feature
│   │   │   │   ├── aliases/               # Teleport alias storage
│   │   │   │   ├── backend/               # Ranker and enumerator backends
│   │   │   │   ├── git/                   # Git status enrichment for search
│   │   │   │   ├── index/                 # Candidate pool / in-memory index
│   │   │   │   ├── model/                 # Search candidate data classes
│   │   │   │   ├── query/                 # Query parsing and path resolution
│   │   │   │   ├── ranking/               # Frecency store and scoring
│   │   │   │   └── ui/                    # Quick Open popup and result UI
│   │   │   ├── remote/                    # SFTP connection and remote I/O
│   │   │   │   ├── git/                   # Remote VCS integration
│   │   │   │   ├── security/              # Credential storage, audit logging
│   │   │   │   ├── settings/              # Remote connection persistence
│   │   │   │   └── ui/                    # Remote browser panel and dialogs
│   │   │   ├── settings/                  # Local browser and Quick Open settings
│   │   │   ├── ui/                        # Core browser UI (ExplorerPanel, BrowserHost)
│   │   │   └── util/                      # Shared utilities
│   │   └── resources/META-INF/
│   │       └── plugin.xml                 # Plugin descriptor (all extension points)
│   └── test/
│       ├── kotlin/ro/faur/explorer/       # Test source (current package)
│       │   ├── heavy/                     # Heavy tests (full IDE environment)
│       │   ├── light/                     # Light tests (light fixture)
│       │   ├── sftp/                      # SFTP integration tests
│       │   ├── ui/                        # UI component tests
│       │   └── unit/                      # Pure unit tests (JUnit5)
│       └── testData/                      # Test fixtures and sample data
│           ├── bookmarks/
│           ├── filetree/basic/
│           └── remote/
├── rust-fuzzy/                            # Rust source for Nucleo native library
├── scripts/                               # Build/release helper scripts
├── .github/workflows/                     # CI pipeline definitions
├── build.gradle.kts                       # Gradle build script
├── settings.gradle.kts                    # Gradle settings
├── gradle.properties                      # IntelliJ Platform version pins
└── bin/                                   # Compiled class output (not committed)
```

## Directory Purposes

**`src/main/kotlin/ro/faur/explorer/`:**
- Purpose: Root package for all plugin Kotlin source
- Contains: Tool window factory, plus feature sub-packages
- Key files: `ExplorerToolWindowFactory.kt`

**`src/main/kotlin/ro/faur/explorer/actions/`:**
- Purpose: All IntelliJ `AnAction` subclasses registered in `plugin.xml`
- Contains: `ExplorerActions.kt` (toggle, open, back, copy/cut/paste, copy-path, rename, delete, refresh), `FileActions.kt`, `NavigationActions.kt`, `QuickOpenAction.kt`, `SwitchPanelAction.kt`, `DragDropHandler.kt`, `FileTreeTransferHandler.kt`, `ExplorerActionUtil.kt`
- Key files: `ExplorerActionUtil.kt` — shared lookup helper used by all actions to find the active `ExplorerPanel`

**`src/main/kotlin/ro/faur/explorer/ui/`:**
- Purpose: Core browser UI components
- Contains: `ExplorerPanel.kt` (root tool window component), `BrowserHost.kt` (tab manager), `BrowserPanel.kt` (abstract base), `LocalBrowserPanel.kt`, `BookmarksPanel.kt`, `FileTreeComponent.kt`
- Key files: `BrowserPanel.kt` — abstract contract all browser panels must implement; `BrowserHost.kt` — CardLayout tab manager

**`src/main/kotlin/ro/faur/explorer/remote/`:**
- Purpose: SFTP connectivity and all remote file system operations
- Contains: `SftpFileOperations.kt`, `SftpFileTreeModel.kt`, `SftpEntry.kt`, `ConnectionProfile.kt`, `ConnectionState.kt`, `CrossPanelTransferService.kt`, `DirectoryCache.kt`, `IntelliJSshConfigProvider.kt`, `SshConfigParser.kt`, `SshTerminalAction.kt`, `TempFileCleanupListener.kt`, `FileSizeLimitChecker.kt`

**`src/main/kotlin/ro/faur/explorer/remote/ui/`:**
- Purpose: Swing UI for remote browsing — panels and dialogs
- Contains: `RemoteBrowserPanel.kt`, `ConnectionDialog.kt`, `ManageConnectionsDialog.kt`, `ImportSshConfigDialog.kt`, `PasswordPromptDialog.kt`, `ConnectionStatusBar.kt`, `LogTailToolWindow.kt`, `RemotePathField.kt`, `RemoteTreeDropTarget.kt`, `RemoteTreeTransferHandler.kt`

**`src/main/kotlin/ro/faur/explorer/remote/git/`:**
- Purpose: VCS provider implementation for remote git repositories over SSH
- Contains: 15+ files implementing IntelliJ VCS APIs (`VcsHistoryProvider`, `DiffProvider`, `AnnotationProvider`, etc.) plus SSH-based git command execution and output parsers

**`src/main/kotlin/ro/faur/explorer/remote/security/`:**
- Purpose: Credential management and audit logging for remote connections
- Contains: `CredentialHandler.kt` (IntelliJ credential store wrapper), `RemoteAuditLogger.kt`

**`src/main/kotlin/ro/faur/explorer/remote/settings/`:**
- Purpose: Persistent settings for remote connections
- Contains: `RemoteConnectionSettings.kt` (project-scoped, stores `ConnectionProfile` list), `RemoteExplorerSettings.kt`, `RemoteExplorerConfigurable.kt`

**`src/main/kotlin/ro/faur/explorer/gitpanel/`:**
- Purpose: "System Explorer Git" tool window — commit log browser with local and remote backends
- Contains: `GitBackend.kt` (interface), `LocalGitBackend.kt`, `RemoteGitBackend.kt`, `GitRepositoryRegistry.kt` (project service), `ActiveBrowserTracker.kt` (project service), `exec/GitCommandExecutor.kt`, `exec/LocalGitCommandExecutor.kt`, `exec/GitCommandResult.kt`
- Key files: `GitBackend.kt` — interface all backends implement; `GitRepositoryRegistry.kt` — manages which repos are known

**`src/main/kotlin/ro/faur/explorer/gitpanel/ui/`:**
- Purpose: Swing components for the Git panel tool window
- Contains: `GitPanelComponent.kt`, `GitPanelToolWindowFactory.kt`, `CommitLogPanel.kt`, `CommitLogTableModel.kt`, `CommitDetailsPanel.kt`, `ChangedFilesPanel.kt`

**`src/main/kotlin/ro/faur/explorer/quickopen/`:**
- Purpose: All Quick Open (Cmd+Shift+P) fuzzy search infrastructure
- Sub-packages documented below

**`src/main/kotlin/ro/faur/explorer/quickopen/backend/`:**
- Purpose: Enumerators (what files to search) and ranker backends (how to score matches)
- Contains: `EnumeratorBackend.kt` (interface), `VfsEnumerator.kt`, `RipgrepEnumerator.kt`, `RankerBackend.kt` (interface), `NucleoRanker.kt`, `NucleoNative.kt` (JNI), `MinusculeMatcherRanker.kt`, `FallbackRanker.kt`, `RankerSelector.kt`, `RipgrepContentSearch.kt`

**`src/main/kotlin/ro/faur/explorer/quickopen/ranking/`:**
- Purpose: Frecency scoring (recency × frequency combined score) persisted across IDE restarts
- Contains: `FrecencyStore.kt` (application service, persists to `explorerFrecency.xml`), `Ranker.kt`

**`src/main/kotlin/ro/faur/explorer/model/`:**
- Purpose: Local filesystem domain model and application-level bookmark storage
- Contains: `FileEntry.kt`, `FileTreeModel.kt` (VFS-backed, filterable), `FileComparator.kt`, `Bookmark.kt`, `BookmarkManager.kt` (application service, persists to `explorerBookmarks.xml`)

**`src/main/kotlin/ro/faur/explorer/settings/`:**
- Purpose: All local-browser and Quick Open settings — configurables and state classes
- Contains: `ExplorerSettings.kt` (persists to `explorerSettings.xml`), `QuickOpenSettings.kt`, `SystemExplorerConfigurable.kt` (parent node), `ExplorerConfigurable.kt`, `QuickOpenConfigurable.kt`

**`src/main/kotlin/ro/faur/explorer/util/`:**
- Purpose: Stateless utility functions
- Contains: `FileSizeFormatter.kt`, `GlobFilter.kt`, `PathUtils.kt`

**`rust-fuzzy/`:**
- Purpose: Rust crate that wraps the Nucleo fuzzy matching library and exposes JNI bindings
- Generated: No (source committed)
- Committed: Yes

**`src/test/testData/`:**
- Purpose: Static test fixtures used by light and heavy tests
- Generated: No
- Committed: Yes

**`bin/`:**
- Purpose: Compiled class output from Gradle
- Generated: Yes
- Committed: No (in `.gitignore`)

## Key File Locations

**Entry Points:**
- `src/main/kotlin/ro/faur/explorer/ExplorerToolWindowFactory.kt`: System Explorer tool window factory
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelToolWindowFactory.kt`: Git panel tool window factory
- `src/main/resources/META-INF/plugin.xml`: All plugin extension point declarations

**Core UI:**
- `src/main/kotlin/ro/faur/explorer/ui/ExplorerPanel.kt`: Root UI component (toolbar, BrowserHost, tab strip)
- `src/main/kotlin/ro/faur/explorer/ui/BrowserHost.kt`: Tab manager with CardLayout panel switching
- `src/main/kotlin/ro/faur/explorer/ui/BrowserPanel.kt`: Abstract base for all browser panels
- `src/main/kotlin/ro/faur/explorer/ui/LocalBrowserPanel.kt`: Local filesystem browser implementation
- `src/main/kotlin/ro/faur/explorer/remote/ui/RemoteBrowserPanel.kt`: Remote SFTP browser implementation

**Actions:**
- `src/main/kotlin/ro/faur/explorer/actions/ExplorerActionUtil.kt`: Central helper for action → panel lookup

**Configuration:**
- `src/main/kotlin/ro/faur/explorer/settings/ExplorerSettings.kt`: Local browser persistent settings
- `src/main/kotlin/ro/faur/explorer/remote/settings/RemoteConnectionSettings.kt`: Per-project SSH connection profiles

**Persistence Services:**
- `src/main/kotlin/ro/faur/explorer/model/BookmarkManager.kt`: Application-scoped bookmark list
- `src/main/kotlin/ro/faur/explorer/quickopen/ranking/FrecencyStore.kt`: Application-scoped frecency data
- `src/main/kotlin/ro/faur/explorer/quickopen/aliases/TeleportAliasStore.kt`: Quick Open named path aliases

**Remote I/O:**
- `src/main/kotlin/ro/faur/explorer/remote/SftpFileOperations.kt`: Core SFTP operations (list, upload, download, rename, delete)
- `src/main/kotlin/ro/faur/explorer/remote/CrossPanelTransferService.kt`: Async cross-panel file transfer (local ↔ remote)

**Git:**
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt`: Backend interface (local/remote git)
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitRepositoryRegistry.kt`: Registry of known repos
- `src/main/kotlin/ro/faur/explorer/gitpanel/ActiveBrowserTracker.kt`: Tracks which browser panel is active for Git context sync

**Build:**
- `build.gradle.kts`: Gradle build with IntelliJ Platform Gradle Plugin
- `gradle.properties`: Platform version, SDK version, plugin version
- `settings.gradle.kts`: Project name and plugin management

## Naming Conventions

**Files:**
- Kotlin class files match their class name exactly (PascalCase): `ExplorerPanel.kt`, `BrowserHost.kt`
- Interface names are descriptive nouns: `GitBackend`, `RankerBackend`, `EnumeratorBackend`
- UI dialog files suffixed with `Dialog`: `ConnectionDialog.kt`, `ManageConnectionsDialog.kt`
- Settings files suffixed with `Settings` (state holder) or `Configurable` (IDE settings panel)
- Parser/executor utility classes suffixed with `Parser`, `Executor`, or `Provider`

**Packages:**
- Feature packages are lowercase single words or compound words: `quickopen`, `gitpanel`, `remote`
- Sub-feature packages named by concern: `backend`, `ranking`, `ui`, `exec`, `security`, `settings`

**Classes:**
- UI components: `*Panel`, `*Component`, `*Renderer`, `*Host`
- IntelliJ actions: `*Action`
- IntelliJ services: named after domain concept (no suffix), companion `getInstance()` pattern
- IntelliJ configurables: `*Configurable`
- Data classes: `*Entry`, `*Profile`, `*State`, `*Info`, `*Result`

## Where to Add New Code

**New browser panel type (e.g., FTP, S3):**
- Extend `BrowserPanel`: `src/main/kotlin/ro/faur/explorer/ui/`
- Add UI components alongside existing panels in same package
- Register connection in `ExplorerPanel.connectToRemote()` or add new button flow

**New file action (keyboard shortcut or context menu item):**
- Add `AnAction` subclass to `src/main/kotlin/ro/faur/explorer/actions/`
- Register in `src/main/resources/META-INF/plugin.xml` under `SystemExplorer.ActionGroup`
- Use `ExplorerActionUtil` to find the active panel

**New Quick Open backend (ranker or enumerator):**
- Implement `RankerBackend` or `EnumeratorBackend` interface
- Place in `src/main/kotlin/ro/faur/explorer/quickopen/backend/`
- Register in `RankerSelector.kt` or `QuickOpenPanel` enumerator selection logic

**New setting:**
- Add field to the appropriate `*Settings.State` data class
- Expose UI control in the matching `*Configurable`
- Application-scoped: `src/main/kotlin/ro/faur/explorer/settings/`
- Project-scoped: `src/main/kotlin/ro/faur/explorer/remote/settings/`

**New persistent application service:**
- Implement `PersistentStateComponent<T>`
- Annotate with `@State(name = "...", storages = [Storage("filename.xml")])`
- Register in `src/main/resources/META-INF/plugin.xml` as `<applicationService>` or `<projectService>`

**New utility:**
- Stateless helper functions: `src/main/kotlin/ro/faur/explorer/util/`

**Tests:**
- Unit tests (no IntelliJ): `src/test/kotlin/ro/faur/explorer/unit/`
- Light platform tests: `src/test/kotlin/ro/faur/explorer/light/`
- Heavy platform tests: `src/test/kotlin/ro/faur/explorer/heavy/`
- Test fixtures: `src/test/testData/`

## Special Directories

**`rust-fuzzy/`:**
- Purpose: Nucleo fuzzy matching JNI library in Rust
- Generated: No
- Committed: Yes
- Note: Must be compiled separately; `.so`/`.dylib` output bundled into plugin JAR

**`bin/`:**
- Purpose: Gradle compiled output (class files)
- Generated: Yes
- Committed: No

**`.planning/`:**
- Purpose: GSD planning documents (codebase analysis, research, phase plans)
- Generated: By GSD tooling
- Committed: Yes

**`.intellijPlatform/localPlatformArtifacts/`:**
- Purpose: Downloaded IntelliJ Platform SDK for testing
- Generated: Yes (by Gradle IntelliJ Plugin)
- Committed: No

---

*Structure analysis: 2026-02-28*
