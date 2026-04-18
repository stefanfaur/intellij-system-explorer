# Changelog

## [Unreleased]
### Added
- Configurable chord-based keyboard shortcuts system for System Explorer
  - Backtick-based chords (e.g., `` `c`` for copy, `` `n`` for new file)
  - Context-aware: same chord does panel-appropriate action based on active panel
  - Visual chord feedback with floating overlay during chord mode
  - Shortcut reference panel (dockable, auto-hides)
  - Settings UI for customization with import/export
  - Space preview (hold-to-preview) for file browser
  - Keyboard shortcut hints for context menu items
- File browser chord shortcuts: copy, cut, paste, delete, rename, new file/folder, edit, terminal, explorer, copy path/name
- Git panel chord shortcuts: cherry-pick, revert, rename branch, new branch, fetch, pull, copy commit hash
- Quick Open chord shortcuts: copy path, open, edit, copy result, refresh index
- Tab switching with Ctrl+Alt+1-4 (alternative to Option+Shift+1-4)
- Git panel shortcuts for cherry-pick, revert, and branch management

### Changed
### Fixed

## [1.1.0] - 2026-03-02

### Added
- Remote SSH/SFTP panels: up to 4 remote browser tabs with full file operations, per-connection bookmarks, directory cache, and configurable keepalive/timeout
- Remote Git status decorations on SSH tree items; remote git settings (poll interval, command timeout, blame limit)
- Git Panel tool window: commit log with graph/subject/date, working-tree row, changed-files list with status codes, inline diff viewer
- Stage/unstage files, write commit message, and push from the Git Panel
- Branch management: create, checkout, and delete branches; hard reset; BranchNameValidator
- Stash UI: create stash with optional message and include-untracked, apply, and drop
- Pull button with background task (up-to-date / fast-forward / conflict result paths)
- Git Panel auto-detects local repos and syncs repo selector with the active browser tab
- Quick Open v2: fuzzy popup (Cmd+Shift+P) with frecency ranking, query language, speed dial, and preview pane
- Query language: `d:` dirs, `f:` files, `b:` bookmarks, `r:` recent, `>` IDE actions, `~` regex, `/:` content search; `@ext:` and `@in:` modifiers
- Lucene hybrid index for fast content search with index health dashboard (stats, rebuild, browse) in Settings
- Ripgrep content search with line-precise editor navigation and match highlighting in results
- Teleport aliases: map short names to absolute paths in Settings → Quick Open
- Index mode chip in Quick Open status bar (shows active enumerator: Lucene / VFS)
- VCS color coding on local file tree (modified=orange, added=green, deleted=red, renamed=blue)
- Context menu additions: Reveal in Finder, Open Terminal Here, Compare With…
- Drag-and-drop polish: ghost drag image, conflict pre-check dialog, balloon error notifications; native AWT drops from external file managers
- Panel switching shortcuts: Option+Shift+1 (Local), Option+Shift+2–5 (SSH panels)
- Additional settings: sort by name/size/modified/type, status bar detail levels (minimal/normal/verbose), file permissions display, show item count in folders, show file size in tree, expand on single click, remember last path, glob filter presets

### Changed
- Quick Open shortcut changed from Ctrl+Shift+O to Cmd+Shift+P
- Toolbar reorganized with icon-only row; hidden-files and permissions toggles moved to status bar

## [1.0.0] - 2026-02-15

### Added
- Full-featured system file browser as an IntelliJ tool window
- Browse, copy, move, rename, and delete files across the entire filesystem
- Bookmarks sidebar for quick access to frequently used directories
- Drag-and-drop support between System Explorer and Project view
- Quick Open dialog (Ctrl+Shift+O) for fast directory navigation by path
- Context menu with all file operations (copy, cut, paste, rename, delete, copy path)
- Filter files by glob pattern
- Status bar displaying selection info and current directory details
- Settings panel with configurable options:
  - Show/hide hidden files
  - Sort folders first
  - Confirm before delete
  - Delete to system trash
- Keyboard shortcuts:
  - Alt+E to toggle System Explorer panel
  - Ctrl+C / Ctrl+X / Ctrl+V for copy, cut, paste
  - F2 to rename
  - Delete to delete
  - F5 to refresh
  - Ctrl+Shift+O to quick open directory
  - Ctrl+Shift+C to copy file path
