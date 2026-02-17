# System Explorer

[![CI](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/ci.yml/badge.svg)](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/ci.yml)
[![Release](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/publish.yml/badge.svg)](https://github.com/stefanfaur/intellij-system-explorer/actions/workflows/publish.yml)

A full-featured system file browser plugin for IntelliJ-based IDEs. Browse, copy, move, and manage files across your entire filesystem without leaving the IDE.

## Table of Contents

- [Installation](#installation)
  - [Install from Disk (Local Build)](#install-from-disk-local-build)
  - [Run in Development Sandbox](#run-in-development-sandbox)
- [Getting Started](#getting-started)
- [Features](#features)
  - [File Tree Browser](#file-tree-browser)
  - [Bookmarks Sidebar](#bookmarks-sidebar)
  - [File Operations](#file-operations)
  - [Drag and Drop](#drag-and-drop)
  - [Quick Open Dialog](#quick-open-dialog)
  - [Filter Files](#filter-files)
  - [Status Bar](#status-bar)
  - [Settings](#settings)
- [Keyboard Shortcuts](#keyboard-shortcuts)
- [UI Layout](#ui-layout)
- [Building from Source](#building-from-source)
- [Running Tests](#running-tests)
- [Architecture](#architecture)
- [Compatibility](#compatibility)

## Installation

### Install from Disk (Local Build)

1. **Build the plugin:**

   ```bash
   ./gradlew buildPlugin
   ```

   This produces a ZIP file at `build/distributions/intellij-explorer-1.0.0.zip`.

2. **Install in IntelliJ:**

   - Open IntelliJ IDEA (or any JetBrains IDE)
   - Go to **Settings** (Ctrl+Alt+S) > **Plugins**
   - Click the **gear icon** (top-right) > **Install Plugin from Disk...**
   - Select `build/distributions/intellij-explorer-1.0.0.zip`
   - Click **OK** and **restart the IDE** when prompted

3. **Open the tool window:**

   After restart, the **System Explorer** tool window is available on the right sidebar. Click it, or press **Alt+E** to toggle it.

### Run in Development Sandbox

To test the plugin without installing it permanently, use the Gradle `runIde` task. This launches a separate IntelliJ instance with the plugin loaded:

```bash
./gradlew runIde
```

This opens a fresh IntelliJ Community Edition instance with System Explorer pre-installed. Changes to the code are reflected after re-running the task. This is the recommended approach during development.

## Getting Started

1. **Open the tool window:** Press **Alt+E** or click **System Explorer** in the right sidebar.
2. **Browse files:** The file tree starts at your home directory. Expand folders by clicking the arrow or double-clicking.
3. **Navigate into a folder:** Double-click a directory to make it the new root of the tree.
4. **Go up:** Click the **Up** button in the toolbar to go to the parent directory.
5. **Go home:** Click the **Home** button to return to your home directory.
6. **Open a file:** Double-click a file to open it in the editor.

## Features

### File Tree Browser

The main panel displays a filesystem tree view starting from a configurable root directory (defaults to your home directory).

- **Lazy loading:** Subdirectories are loaded on expansion, not upfront, so large directories don't cause lag.
- **Sorting:** Folders are listed before files (configurable). Both groups are sorted alphabetically, case-insensitive.
- **Hidden files:** Dotfiles and hidden directories are hidden by default. Toggle with the **Hidden** button in the toolbar.
- **File type icons:** Files show their IntelliJ file type icon. Directories show a folder icon.
- **Speed search:** Start typing while the tree is focused to jump to matching entries (IntelliJ's built-in TreeSpeedSearch).

### Bookmarks Sidebar

A collapsible sidebar on the left side of the tool window shows bookmarked directories for quick access.

- **Default bookmarks:** Home, Desktop, Downloads are added on first run.
- **Click to navigate:** Click a bookmark to navigate the tree to that directory.
- **Add bookmarks:** Right-click any directory in the tree > **Add to Bookmarks**. The sidebar refreshes automatically.
- **Persistence:** Bookmarks are stored across IDE restarts.

### File Operations

Right-click any file or folder in the tree to access the full context menu:

| Action | Description |
|--------|-------------|
| **Open** | Open the selected file in the editor (files only) |
| **Open in System** | Open the file/folder in your OS default application (Finder, Explorer, etc.) |
| **Copy** | Copy selected files to clipboard |
| **Cut** | Cut selected files (move on paste) |
| **Paste** | Paste files from clipboard into the current directory |
| **Copy Path** | Copy the absolute path to the system clipboard |
| **Rename** | Rename the selected file or folder (shows input dialog) |
| **Delete** | Delete selected files (with confirmation dialog) |
| **New File** | Create a new file in the current directory (shows input dialog) |
| **New Folder** | Create a new folder in the current directory (shows input dialog) |
| **Add to Bookmarks** | Add a directory to the bookmarks sidebar (directories only) |
| **Refresh** | Refresh the file tree from disk |

**Clipboard interop:** Files copied in System Explorer can be pasted in the IDE's Project view, and vice versa. The clipboard uses `javaFileListFlavor` for compatibility.

**Delete behavior:** By default, files are moved to the system trash (Recycle Bin on Windows, Trash on macOS/Linux). If trash is not supported, files are permanently deleted. This is configurable in Settings.

**Duplicate detection:** Creating a file or folder with a name that already exists shows a warning dialog instead of silently failing.

### Drag and Drop

Drag files between System Explorer and other IDE views:

- **Drag from Explorer to Project view:** Copies files to the target directory.
- **Drag from Project view to Explorer:** Copies files to the target directory.
- **Hold Shift while dropping:** Moves files instead of copying.
- **Drag within Explorer:** Drop files onto a directory to copy/move them there.

The drop target highlights valid directories. Dropping onto a file targets its parent directory. Dropping in empty space is rejected. Drag-and-drop uses both IntelliJ's DnDManager (for IDE interop) and Swing's TransferHandler (for reliable within-tree and cross-component DnD).

### Quick Open Dialog

Press **Cmd+Shift+P** from anywhere in the IDE to open the Quick Open dialog. This provides fast directory navigation without browsing the tree:

- **Type a path:** Enter any absolute directory path in the text field.
- **Browse button:** Click **...** to open a native directory chooser dialog.
- **Recent directories:** The dialog shows your recently visited directories. Click one to select it.
- **Validation:** The dialog validates that the path exists and is a directory before accepting.

### Filter Files

The filter text field below the toolbar accepts glob patterns to filter visible files:

- `*.kt` — Show only Kotlin files
- `*.java;*.kt` — Show Java and Kotlin files (semicolon-separated patterns)
- `test*` — Show files starting with "test"
- `file?.txt` — Single character wildcard

Press **Enter** in the filter field to apply. Directories always remain visible for navigation. Clear the filter to show all files again.

### Status Bar

The bottom of the tool window shows contextual information:

- **No selection:** Shows the count of folders and files in the current directory (e.g., "3 folders, 12 files").
- **Files selected:** Shows the number of selected items and their total size (e.g., "2 selected -- 14.5 KB").
- **Directories selected:** Shows child count and immediate size (e.g., "1 selected -- 5 items, 2.0 KB").
- **Mixed selection:** Shows directory item count and file size (e.g., "3 selected -- 10 items in dirs, 4.0 KB in files").

File sizes are auto-formatted (B, KB, MB, GB, TB).

### Settings

Access via **Settings** (Ctrl+Alt+S) > **Tools** > **System Explorer**, or click the **Settings** button in the toolbar.

| Setting | Default | Description |
|---------|---------|-------------|
| **Show hidden files** | Off | Show dotfiles (`.gitignore`, `.hidden/`, etc.) in the tree |
| **Sort folders first** | On | List directories before files. When off, all entries are sorted alphabetically |
| **Show file size in tree** | On | Display file sizes next to filenames in the tree |
| **Show file permissions** | Off | Display file permission info in the tree |
| **Expand directories on single click** | On | Expand/collapse directories with a single click |
| **Remember last visited path** | On | Restore the last visited directory when reopening the tool window |
| **Confirm before delete** | On | Show a confirmation dialog before deleting files |
| **Delete to trash** | On | Move deleted files to the system trash instead of permanently deleting them |
| **Default root path** | *(empty = home)* | The directory shown when the tool window first opens. Uses a directory chooser dialog. Leave empty for your home directory |

Settings are organized into Display, Behavior, Delete Behavior, and Paths groups. Persisted in `explorerSettings.xml` and apply across all projects.

## Keyboard Shortcuts

All shortcuts are configurable via **Settings** > **Keymap** > search "System Explorer".

| Shortcut | Action | Scope |
|----------|--------|-------|
| **Alt+E** | Toggle System Explorer tool window | Global (works from any view) |
| **Ctrl+C** | Copy selected files to clipboard | When System Explorer is focused |
| **Ctrl+X** | Cut selected files (move on paste) | When System Explorer is focused |
| **Ctrl+V** | Paste files from clipboard | When System Explorer is focused |
| **Ctrl+Shift+C** | Copy absolute path to clipboard | When System Explorer is focused |
| **Cmd+Shift+P** | Open Quick Open Directory dialog | Global (works from any view) |
| **F2** | Rename selected file | When System Explorer is focused |
| **Delete** | Delete selected files | When System Explorer is focused |
| **F5** | Refresh file tree | When System Explorer is focused |

**Note on shortcut scoping:** Alt+E and Cmd+Shift+P work globally from anywhere in the IDE. All other shortcuts only activate when the System Explorer tool window is focused. When unfocused, they fall through to their default IDE bindings (e.g., Ctrl+C in the editor copies text as usual).

## UI Layout

```
┌─────────────────────────────────────────────────────────┐
│ [<] [>] [Up] [Home] [Refresh] [Hidden] [Settings]      │  Buttons
│ [/path/to/current/directory                           ] │  Path bar
│ [Filter (e.g. *.kt)                                  ] │  Filter bar
├──────────────┬──────────────────────────────────────────┤
│  Home        │  > bin/                                  │
│  Desktop     │  > etc/                                  │  Bookmarks | File Tree
│  Downloads   │  > lib/                                  │
│              │    README.md                              │
│              │    build.gradle.kts                       │
├──────────────┴──────────────────────────────────────────┤
│  3 folders, 2 files                                     │  Status bar
└─────────────────────────────────────────────────────────┘
```

- **Toolbar (top):** Three rows — navigation buttons (Back, Forward, Up, Home, Refresh, Hidden toggle, Settings), a full-width editable path bar, and a filter field.
- **Path bar:** Type a path and press Enter to navigate directly.
- **Filter bar:** Glob pattern filter. Press Enter to apply.
- **Bookmarks (left):** Click to navigate. Right-click directories in the tree to add bookmarks.
- **File tree (right):** The main filesystem view. Supports right-click context menu, double-click, drag-and-drop, and keyboard navigation.
- **Status bar (bottom):** Selection info and file counts.

## Building from Source

**Prerequisites:**
- Java 21 (JDK)
- Gradle 8.13+ (included via wrapper)

```bash
# Clone the repository
git clone <repository-url>
cd intellij-explorer

# Build the plugin ZIP
./gradlew buildPlugin

# Output: build/distributions/intellij-explorer-1.0.0.zip

# Run a development IDE instance with the plugin loaded
./gradlew runIde
```

### Other Useful Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew buildPlugin` | Build the plugin ZIP for distribution |
| `./gradlew runIde` | Launch a sandboxed IDE with the plugin installed |
| `./gradlew test` | Run headless-safe unit tests |
| `./gradlew test --tests "ro.faur.explorer.unit.GlobFilterTest"` | Run a specific unit test class |
| `./gradlew verifyPlugin` | Run JetBrains plugin verification checks |
| `./gradlew signPlugin` | Sign the plugin for marketplace distribution |
| `./gradlew publishPlugin` | Publish to the JetBrains Marketplace |

## Running Tests

The test suite has 160 tests organized in a four-layer pyramid:

```bash
# Run headless-safe unit tests
./gradlew test

# Run only pure unit tests (no IDE, sub-second)
./gradlew test --tests "ro.faur.explorer.unit.*"

# Run light platform tests (minimal IDE environment)
./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.light.*"

# Run heavy platform tests (full project environment)
./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.heavy.*"

# Run UI integration tests (requires running IDE via RemoteRobot)
./gradlew testUi

# Run the full suite used in CI
./gradlew test
./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.light.*"
./gradlew test -PincludeIdeTests=true --tests "ro.faur.explorer.heavy.*"
./gradlew testUi
```

### Test Layers

| Layer | Count | Speed | What It Tests |
|-------|-------|-------|---------------|
| **Unit** (`unit/`) | ~30 | < 1s | FileSizeFormatter, FileComparator, GlobFilter, PathUtils — no IDE dependency |
| **Light Platform** (`light/`) | ~100 | ~5s | ExplorerSettings, BookmarkManager, FileActions, NavigationActions, FileTreeModel, DragDropHandler, QuickOpenDialog, tool window registration — uses `BasePlatformTestCase` |
| **Heavy Platform** (`heavy/`) | ~10 | ~2s | Tool window lifecycle, clipboard interop — uses `HeavyPlatformTestCase` |
| **UI Integration** (`ui/`) | ~10 | minutes | End-to-end tool window interaction via RemoteRobot (excluded from `./gradlew test`, run via `./gradlew testUi`) |

## Architecture

```
src/main/kotlin/com/github/stefanfaur/explorer/
├── ExplorerToolWindowFactory.kt    # Tool window entry point (registered in plugin.xml)
├── actions/
│   ├── DragDropHandler.kt          # DnDSource + DnDTarget for IntelliJ DnD interop
│   ├── ExplorerActionUtil.kt       # Utility to find ExplorerPanel from AnActionEvent
│   ├── FileTreeTransferHandler.kt  # Swing TransferHandler for reliable drag-and-drop
│   ├── ExplorerActions.kt          # 8 AnAction subclasses for keyboard shortcuts
│   ├── FileActions.kt              # Stateless file operations (copy, move, delete, rename)
│   ├── NavigationActions.kt        # Navigation utilities + NavigationHistory
│   └── QuickOpenAction.kt          # Action to open the Quick Open dialog
├── model/
│   ├── Bookmark.kt                 # Bookmark data class
│   ├── BookmarkManager.kt          # Persistent bookmarks storage (PersistentStateComponent)
│   ├── FileComparator.kt           # File sorting (folders first, case-insensitive alpha)
│   ├── FileEntry.kt                # Lightweight file model for sorting
│   └── FileTreeModel.kt            # Tree model with filtering and hidden file support
├── settings/
│   ├── ExplorerConfigurable.kt     # Settings UI panel (Kotlin UI DSL)
│   └── ExplorerSettings.kt         # Persistent settings (PersistentStateComponent)
├── ui/
│   ├── BookmarksPanel.kt           # Bookmarks sidebar list
│   ├── ExplorerPanel.kt            # Main panel (toolbar + tree + bookmarks + status bar)
│   ├── FileTreeComponent.kt        # File tree wrapper (JTree + context menu + DnD)
│   └── QuickOpenDialog.kt          # Quick Open directory dialog
└── util/
    ├── FileSizeFormatter.kt        # Human-readable file size formatting
    ├── GlobFilter.kt               # Glob pattern file filtering
    └── PathUtils.kt                # Path utilities (breadcrumbs, home, hidden detection)
```

**Key design decisions:**
- All file access goes through IntelliJ's **Virtual File System (VFS)** for change notifications, editor integration, and undo support.
- Write operations use `runWriteAction {}` to ensure thread safety with IntelliJ's threading model.
- The tree uses **lazy loading** via `TreeWillExpandListener` — children are loaded only when a directory is expanded.
- All components implement **Disposable** for proper listener cleanup and memory leak prevention.
- Settings and bookmarks use **PersistentStateComponent** for automatic XML serialization across IDE restarts.

## Compatibility

| Property | Value |
|----------|-------|
| **Plugin version** | 1.0.0 |
| **Platform** | IntelliJ Platform 2025.1+ (build 251 through 253.*) |
| **Compatible IDEs** | IntelliJ IDEA, WebStorm, PyCharm, GoLand, CLion, PhpStorm, Rider, RubyMine, DataGrip, and all other JetBrains IDEs |
| **Java** | 21+ |
| **Kotlin** | 2.1.0 |

## CI/CD Pipeline

This project uses GitHub Actions for continuous integration and delivery:

- **CI Workflow**: Runs on every push
  - Unit, light, heavy, and UI tests
  - Plugin verification
  - Snapshot builds on main branch

- **Publish Workflow**: Manual or tag-triggered releases
  - Comprehensive testing and verification
  - Manual approval gate
  - Automated versioning and changelog extraction
  - Publishes to JetBrains Marketplace

See [.github/TESTING.md](.github/TESTING.md) for testing guide.
See [.github/SETUP.md](.github/SETUP.md) for setup instructions.

## License

See [LICENSE](LICENSE) for details.
