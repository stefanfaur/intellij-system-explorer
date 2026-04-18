# System Explorer

[![CI](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/ci.yml/badge.svg)](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/ci.yml)
[![Release](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/publish.yml/badge.svg)](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/publish.yml)

File browser plugin for IntelliJ-based IDEs. Local and remote (SSH/SFTP) browsing, fuzzy Quick Open, and a Git panel — all without leaving the IDE.

---

## Table of Contents

- [Installation](#installation)
- [Tool Windows](#tool-windows)
- [Global Shortcuts](#global-shortcuts)
- [Local Browser](#local-browser)
- [Quick Open](#quick-open)
- [Remote SSH Panels](#remote-ssh-panels)
- [Git Panel](#git-panel)
- [Settings](#settings)
- [Building & Development](#building--development)

---

## Installation

**From disk (local build):**

```bash
./gradlew buildPlugin
# Output: build/distributions/intellij-explorer-*.zip
```

Settings → Plugins → ⚙ → Install Plugin from Disk → select the ZIP → restart.

**Development sandbox:**

```bash
./gradlew runIde
```

---

## Tool Windows

| Window | Location | Toggle |
|--------|----------|--------|
| **System Explorer** | Right sidebar | `Alt+E` |
| **System Explorer Git** | Left sidebar | Click tab |

---

## Global Shortcuts

| Shortcut | Action |
|----------|--------|
| `Alt+E` | Toggle / focus System Explorer |
| `Cmd+Shift+P` | Open Quick Open popup |
| `Option+Shift+1` – `Option+Shift+5` | Switch to panel: 1=Local, 2–5=SSH |
| `Ctrl+Alt+1` – `Ctrl+Alt+4` | Switch to panel 1-4 (alternative) |

All shortcuts configurable via Settings → Keymap → "System Explorer".

### Chord Shortcuts

System Explorer uses **chord shortcuts** for context-aware actions. Press the backtick (`` ` ``) key followed by a letter to trigger an action. The active panel determines what each chord does.

Press `` `/`` to show the shortcut reference panel with all available shortcuts for the current context.

#### File Browser (Local/Remote)

| Chord | Action |
|-------|--------|
| `` `c`` | Copy selected files |
| `` `x`` | Cut selected files |
| `` `v`` | Paste files |
| `` `d`` | Delete selected |
| `` `r`` | Rename selected |
| `` `n`` | New file |
| `` `N`` | New folder (local only) |
| `` `f`` | Refresh |
| `` `o`` | Open selected |
| `` `e`` | Edit in IDE |
| `` `p`` | Show in terminal |
| `` `t`` | Show in explorer |
| `` `y`` | Copy path |
| `` `/`` | Show shortcuts reference |

#### Git Panel

| Chord | Action |
|-------|--------|
| `` `c`` | Cherry-pick selected commit |
| `` `x`` | Revert selected commit |
| `` `r`` | Rename branch |
| `` `n`` | New branch |
| `` `f`` | Fetch from remote |
| `` `p`` | Pull from remote |
| `` `y`` | Copy commit hash |
| `` `/`` | Show shortcuts reference |

#### Quick Open

| Chord | Action |
|-------|--------|
| `` `c`` | Copy selected path |
| `` `o`` | Open selected |
| `` `e`` | Edit/rename selected |
| `` `y`` | Copy result |
| `` `f`` | Refresh index |
| `` `/`` | Show shortcuts reference |

---

## Local Browser

### Toolbar (left to right)

`←` Back · `→` Forward · `↑` Up · `🏠` Home · `↻` Refresh · `📡` Connect SSH · `⚙` Settings

### Path & Filter Bar

- **Path field** — editable; press `Enter` to navigate.
- **Filter field** — glob pattern (e.g. `*.kt`, `*.java;*.kt`). Press `Enter` to apply.
- **Preset combo** — saved glob presets (managed in Settings).

### File Tree

| Shortcut | Action |
|----------|--------|
| `Enter` | Open file / navigate into directory |
| `Alt+Left` | Navigate up one level |
| `Backspace` | Go back |
| `F2` | Rename |
| `Delete` | Delete |
| `F5` | Refresh |
| `Ctrl+C` | Copy |
| `Ctrl+X` | Cut |
| `Ctrl+V` | Paste |
| `Ctrl+Shift+C` | Copy absolute path |

- Lazy-loads directories on expansion.
- Speed search: start typing in a focused tree to jump to matching entries.
- Multi-select: `Ctrl+Click` / `Cmd+Click`.

### Context Menu (right-click)

Open · Open in System · Copy · Cut · Paste · Copy Path · Rename · Delete · New File · New Folder · Add to Bookmarks · Refresh

### Bookmarks Sidebar

- Default bookmarks: Home, Desktop, Downloads.
- Click to navigate. Drag to reorder. Right-click to remove.
- Right-click a directory in the tree → **Add to Bookmarks**.

### Status Bar

Shows item count, file sizes (based on detail level setting), and optional permissions.

- **Hidden files toggle** — icon button at bottom-right.
- **Permissions toggle** — icon button at bottom-right (macOS/Linux only).

### Drag and Drop

- Tree → Project View: copy files.
- Project View → Tree: copy files.
- Tree → Tree: copy/move between panels.
- Hold `Shift` while dropping to move instead of copy.
- External file manager → Tree: native AWT drops supported.

---

## Quick Open

Open with `Cmd+Shift+P`.

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` | Navigate to selected |
| `Cmd+Enter` | Open with… submenu |
| `Alt+Enter` | Context menu |
| `Tab` | Toggle preview pane |
| `Ctrl+R` | Cycle recent queries |
| `Cmd+D` | Toggle bookmark on selected |
| `Cmd+N` | New file or folder |
| `Cmd+[` | Remove last path segment from query |
| `Cmd+C` | Copy absolute path |
| `Cmd+Shift+C` | Copy relative path |
| `Cmd+1` – `Cmd+6` | Activate speed-dial item / Nth result |
| `Ctrl+P` / `Ctrl+N` | Move selection up / down |
| `Esc` | Close |

### Query Language

| Prefix | Mode |
|--------|------|
| *(none)* | Unified: dirs + files + recent + bookmarks |
| `d:` | Directories only |
| `f:` | Files only |
| `b:` | Bookmarks only |
| `r:` | Recent only |
| `>` | IDE actions / commands |
| `~` | Regex match |
| `/:` | Content search (ripgrep) |

**Modifiers** (append to any mode):

| Modifier | Effect |
|----------|--------|
| `@ext:EXT` | Filter by extension, e.g. `@ext:kt` |
| `@in:PATH` | Scope to directory, e.g. `@in:/src` |

**Examples:**

```
d: src                  # directories matching "src"
f: ~ test.*\.kt         # regex: Kotlin test files
/: TODO                 # content search for "TODO"
b: proj @ext:kt         # bookmarks with "proj", .kt files only
```

### Speed Dial

Shown when the query is empty. Displays top frecency items (bookmarks preferred, fallback to recent). `Cmd+1`–`Cmd+6` to activate.

### Frecency Ranking

Results ranked by combined frequency + recency. Weights and decay configurable in Settings → Quick Open.

---

## Remote SSH Panels

- Panels 1–4 are SSH/SFTP panels. Connect via the `📡` toolbar button or `Option+Shift+1`–`Option+Shift+5`.
- Each panel has the same UI as the local browser: path bar, filter, bookmarks, tree, status bar.
- Bookmarks are stored per connection.
- All file operations (copy, cut, paste, rename, delete, new file/folder) work over SFTP.
- Git status decorations appear on tree items when Remote Git integration is enabled.
- Drag-drop between local and remote panels supported.

### SSH Connection Management

SSH connections are configured in **Settings → Tools → System Explorer → Remote SSH**. The `📡` button shows saved connections by `user@host`; click to connect or switch.

---

## Git Panel

Open via the **System Explorer Git** tool window (left sidebar).

### Repository Selector

- Dropdown lists all registered local and remote git repos.
- Auto-detects local repos when browsing directories.
- **+ button** — add a local repo manually.
- **− button** — unregister the selected repo.
- **↻ button** — reload git data.

### Commit Log

Table with Graph · Subject · Date columns.

- **Search field** — filter by commit subject or author.
- **Working tree row** — shows count of uncommitted changes; click to view them.
- Click a row to load commit details.

### Changed Files Panel

Lists files in the selected commit with status codes:

| Code | Meaning |
|------|---------|
| M | Modified |
| A | Added |
| D | Deleted |
| R | Renamed (shows old path) |
| C | Copied |
| ? | Untracked |
| U | Unmerged |

### Commit Details Panel

Shows hash · author · date · full message.

### Sync with Browser

The Git panel automatically tracks the active browser tab — switching from a local to a remote panel updates the repo selector accordingly.

---

## Settings

**Settings → Tools → System Explorer**

### Local Browser

| Setting | Default | Description |
|---------|---------|-------------|
| Show hidden files | Off | Show dotfiles |
| Sort folders first | On | Directories before files |
| Show file size in tree | On | Size next to filename |
| Show file permissions | Off | Octal permissions (Unix/Mac) |
| Show item count in folders | Off | e.g. `src (12)` |
| Sort by | name | name / size / modified / type |
| Status bar detail | normal | minimal / normal / verbose |
| Expand on single click | On | Single click expands directories |
| Remember last path | On | Restore last directory on reopen |
| Confirm before delete | On | Confirmation dialog on delete |
| Delete to trash | On | Trash instead of permanent delete |
| Default root path | *(home)* | Initial directory |
| Glob filter presets | — | Saved name → pattern pairs |

### Quick Open

| Setting | Default | Description |
|---------|---------|-------------|
| Max index size | 50 000 | In-memory file cap |
| Max displayed results | 50 | Ranked candidates shown |
| Speed-dial count | 6 | Items shown on empty query |
| Recent query history | 20 | Queries cycled by `Ctrl+R` |
| Enumeration timeout (s) | 10 | Kill indexing after N seconds |
| ripgrep path | *(auto)* | Path to `rg` binary |
| Max content results | 200 | Max `/:` matches |
| Search hidden files | Off | `--hidden` flag for ripgrep |
| Follow symlinks | Off | `--follow` flag |
| Respect .gitignore | On | Uncheck to add `--no-ignore` |
| Max search depth | 0 (unlimited) | Ripgrep recursion limit |
| Additional ripgrep flags | — | Raw flags appended to every call |
| Recency decay half-life (h) | 24 | Lower = favor recent more |
| Recency vs frequency weight | 60 | Slider: 0=pure freq, 100=pure recency |
| Show scorer debug scores | Off | Raw frecency scores in results |
| Teleport aliases | — | Alias → absolute path shortcuts |

### Remote SSH

| Setting | Default | Description |
|---------|---------|-------------|
| SSH keepalive interval (s) | 60 | Heartbeat interval |
| Keepalive max failures | 3 | Failed pings before reconnect |
| Connection timeout (s) | 10 | Initial connect timeout |
| Connection retry attempts | 2 | Retries on failure |
| Directory cache TTL (s) | 30 | SFTP listing cache lifetime |
| Max cached directories | 200 | In-memory cache size |
| Max file size for editor (MB) | 10 | Don't open larger files |
| Tail log initial lines | 1000 | Lines loaded for tail view |
| Show SSH config hosts | On | Parse `~/.ssh/config` |
| Secure delete sensitive files | Off | Overwrite before delete |

### Remote Git

| Setting | Default | Description |
|---------|---------|-------------|
| Enable git integration | On | Toggle remote git decorations |
| Status refresh interval (s) | 30 | How often to poll git status |
| Command timeout (s) | 15 | Max time for git commands |
| Max blame lines (0=unlimited) | 5000 | Limit blame computation |
| Show git colors on tree | On | M=orange, A=green, D=red, R=blue |
| Show branch in status bar | On | Branch name in IDE status bar |
| Disable on repo size (0=never) | 0 | Auto-disable for large repos |

---

## Building & Development

**Prerequisites:** Java 21, Gradle 8.13+ (wrapper included).

```bash
./gradlew buildPlugin       # Build ZIP for distribution
./gradlew runIde            # Launch sandbox IDE with plugin loaded
./gradlew test              # Run headless unit tests
./gradlew verifyPlugin      # JetBrains plugin verification
./gradlew publishPlugin     # Publish to JetBrains Marketplace
```

**Test layers:**

| Layer | Command | Speed |
|-------|---------|-------|
| Unit | `./gradlew test --tests "ro.faur.explorer.unit.*"` | < 1 s |
| Light platform | `./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.light.*"` | ~5 s |
| Heavy platform | `./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.heavy.*"` | ~2 s |
| UI integration | `./gradlew testUi` | minutes |

**Compatibility:** IntelliJ Platform 2025.1+ · Java 21 · Kotlin 2.1.0

---

## License

See [LICENSE](LICENSE) for details.
