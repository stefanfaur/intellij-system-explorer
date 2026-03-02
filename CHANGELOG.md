# Changelog

## [Unreleased]
### Added
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
