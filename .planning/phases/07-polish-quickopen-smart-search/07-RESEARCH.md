# Phase 7: Polish, improve quick open / smart search - Research

**Researched:** 2026-02-28
**Domain:** IntelliJ Plugin — Swing UI, JNI native bundling, Kotlin coroutines, ripgrep integration
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Search Transparency:** Add a status bar line below the result list (replacing/extending the current hint bar area). Format: `Fuzzy: Nucleo · Root: ~/projects/myapp · 14,203 files`. The root directory path is clickable to open a directory picker to change search root. When Nucleo unavailable: do NOT surface a warning — fix is bundling the native lib so it always works.
- **Nucleo Native Bundling (Root Fix):** Add a Gradle `copyNatives` task copying `rust-fuzzy/target/release/libfuzzyjni.*` into `src/main/resources/natives/<platform>/` before `processResources`. Platform detection: macOS (arm64/x86_64), Linux (x86_64), Windows (x86_64). This is the **highest priority fix**.
- **rg Content Search:** Fix the coroutine scope leak — `performContentSearch()` uses `CoroutineScope(Dispatchers.IO + Job())` (unparented). Replace with structured concurrency tied to the panel's lifecycle. Each rg match shows filename (bold) + line number + matching line with matched portion highlighted. Selecting a rg content match: Enter opens the file in IntelliJ's editor at the exact matched line number. Keep separate debounce for rg: 300ms (vs 50ms for fuzzy).
- **Ranking Quality:** Frecency boost — balanced, fuzzy score can still overtake. Result grouping order: Recent/bookmarks first → files → directories. Match character highlighting in both filename and path segment. Nucleo bundling is prerequisite.
- **UI/UX:** Search debounce: 50ms for fuzzy, 300ms for rg. Hint bar: condense to 5 essential shortcuts: `↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close`. Newly-added status bar replaces space freed by condensed hint bar.
- **Speed Dial (empty query panel):** Show top frecent paths from `FrecencyStore` (most recently visited via Quick Open). Include both files AND directories. Drop bookmark-priority logic; pure recency from `FrecencyStore`.
- **Code Quality (all four required):**
  1. Coroutine scope leak in `performContentSearch()` — fix to structured concurrency
  2. `lateinit popup` crash risk — change to `var popup: JBPopup? = null` with null-safe calls
  3. Bundle Nucleo native libs via Gradle `copyNatives` task
  4. `ripgrepExtraFlags` allowlist validation against safe allowlist (`--hidden`, `--no-ignore`, `--follow`, `--type`, `--glob`); reject `--exec` and non-allowlisted `--`-prefixed flags

### Claude's Discretion

- Exact Gradle platform detection logic and cross-compilation scenario handling
- Whether to add a `?` key for full shortcut help overlay (user said condense, not remove)
- Exact frecency score formula tuning (weights for recency vs frequency)
- How to handle the root picker UX when clicking root path (dialog vs inline field vs dropdown of recent roots)

### Deferred Ideas (OUT OF SCOPE)

- Scope presets (project root / current dir / home shortcuts)
- Remote SFTP content search
- IntelliJ action search improvements beyond the existing `>` prefix
</user_constraints>

## Summary

Phase 7 is a pure polish and correctness phase for the existing Quick Open popup. All the building blocks are already coded in the codebase — the work is fixing bugs (coroutine scope leak, `lateinit popup` crash, Nucleo native not bundled into JAR, rg flag injection risk), completing missing features (status bar with ranker name + root + file count, speed dial sourced from pure frecency, rg match navigation to line), and tightening UX (debounce timings, condensed hint bar, match highlighting in path segment as well as filename).

The most critical fix is Nucleo native bundling. The Gradle build already has a `cargoRelease` task and a `copyNativeLib` task (lines 133–151 of `build.gradle.kts`), but `copyNativeLib` only copies to `layout.buildDirectory.dir("resources/main/natives/...")` — it does NOT copy to `src/main/resources/natives/`. The `NucleoNative.tryLoad()` code reads from the classpath resource `/natives/<platform>/<libname>`, which requires the file to be placed in `src/main/resources/natives/<platform>/` (or the equivalent output location after `processResources`). The current Gradle task copies to the build output directory, so it should work if `processResources` picks up the build output, but if the native is compiled on CI/CD for one platform only, users on other platforms never get the library. The correct fix for distribution is to pre-compile all platform variants and commit them to `src/main/resources/natives/<platform>/` so they ship inside the plugin JAR.

The coroutine scope leak is straightforward: `CoroutineScope(Dispatchers.IO + Job())` in `performContentSearch()` creates an unparented scope that lives forever. `QuickOpenPanel` implements `Disposable` and already has a `CandidatePool` with a `CoroutineScope(Dispatchers.IO + SupervisorJob())` — the panel should either use that scope or create its own panel-lifecycle scope and cancel it in `dispose()`.

**Primary recommendation:** Fix in priority order: (1) Nucleo native bundling → (2) coroutine scope leak → (3) `lateinit popup` → (4) rg flags allowlist. Then implement status bar, rg result navigation, speed dial from frecency, debounce tuning, and hint bar condensing as a single UI pass.

## Standard Stack

### Core (already in use, no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| IntelliJ Platform SDK | 2025.1+ | JBPopup, JBLabel, ColoredListCellRenderer, OpenFileDescriptor | Platform-native, already in classpath |
| Kotlin coroutines | 1.10.1 (test) | Structured concurrency for rg search | Already used via `kotlinx-coroutines-test` |
| Nucleo (Rust JNI) | existing in `rust-fuzzy/` | Fuzzy scoring native library | Already built; issue is classpath bundling only |
| Ripgrep (`rg`) | system-installed | Content search | Already integrated via ProcessBuilder |
| Gson | bundled by platform | `--json` output parsing from rg | Already used in `RipgrepContentSearch` |

### No New Dependencies

All required functionality uses existing APIs:
- `OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)` — standard IntelliJ line navigation
- `FileChooserDescriptor` + `FileChooserFactory` — directory picker for root change
- `JFileChooser` (Swing) — simpler alternative for root picker
- `JBPopupFactory.getInstance().createListPopup()` — already used for context menus

## Architecture Patterns

### Recommended Project Structure (no changes needed)

```
src/main/
├── kotlin/ro/faur/explorer/quickopen/
│   ├── ui/
│   │   ├── QuickOpenPanel.kt         # Status bar, debounce tuning, popup null-safety, speed dial change
│   │   ├── SpeedDialPanel.kt         # Data source change: frecency-only
│   │   ├── SearchResultRenderer.kt   # Path segment match highlighting (already has filename highlighting)
│   │   └── SearchResult.kt           # No changes needed
│   └── backend/
│       ├── RipgrepContentSearch.kt   # Flags allowlist validation only
│       └── NucleoNative.kt           # No changes needed
├── resources/
│   └── natives/
│       ├── darwin-aarch64/libfuzzyjni.dylib   # MUST be committed here for distribution
│       ├── darwin-x86_64/libfuzzyjni.dylib    # Cross-compile or CI-built
│       ├── linux-x86_64/libfuzzyjni.so
│       └── win32-x86_64/fuzzyjni.dll
build.gradle.kts                               # copyNatives task already exists; verify output path
```

### Pattern 1: Structured Coroutine Scope for Panel Lifecycle

**What:** Replace the fire-and-forget `CoroutineScope(Dispatchers.IO + Job())` in `performContentSearch()` with a panel-owned scope that is cancelled in `dispose()`.

**Current broken code (QuickOpenPanel.kt, line 515):**
```kotlin
CoroutineScope(Dispatchers.IO + Job()).launch {
    // ... collected into matches ...
}
latch.await(10, TimeUnit.SECONDS)
```

**Correct pattern:**
```kotlin
// In QuickOpenPanel class body:
private val panelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// In performContentSearch():
private fun performContentSearch(pattern: String): List<SearchResult> {
    // ...
    val matches = mutableListOf<ContentMatch>()
    val latch = CountDownLatch(1)
    panelScope.launch {
        try {
            RipgrepContentSearch(rgPath, searchScope).search(pattern)
                .take(MAX_CONTENT_RESULTS)
                .collect { matches.add(it) }
        } finally { latch.countDown() }
    }
    latch.await(10, TimeUnit.SECONDS)
    // ...
}

// In dispose():
override fun dispose() {
    previewPane.dispose()
    candidatePool.cancel()
    pendingSearch?.cancel(true)
    searchScheduler.shutdownNow()
    panelScope.cancel()  // ADD THIS
}
```

**Why:** `SupervisorJob()` means one coroutine failure does not cancel siblings. Cancelling in `dispose()` ensures rg processes are killed when the popup closes.

### Pattern 2: Null-Safe Popup Reference

**What:** Change `lateinit var popup: JBPopup` to `var popup: JBPopup? = null` and use null-safe calls.

**Current (line 149):**
```kotlin
lateinit var popup: JBPopup  // set by QuickOpenPopup after creation
```

**Correct:**
```kotlin
var popup: JBPopup? = null  // set by QuickOpenPopup after creation
```

All `popup.closeOk(null)` callsites become `popup?.closeOk(null)`. There are 3 callsites: `activateSelected()`, `speedDialPanel.onActivated`, and `showOpenWithMenu()`.

### Pattern 3: Nucleo Native Bundling in Gradle

**What:** The `copyNativeLib` task (already exists, lines 142–149 of build.gradle.kts) copies the native library to `layout.buildDirectory.dir("resources/main/natives/${detectHostPlatform()}")`. This works at dev time but does NOT produce a cross-platform plugin distribution.

**Current task (already correct for single-platform dev):**
```kotlin
val copyNativeLib by tasks.registering(Copy::class) {
    dependsOn(cargoRelease)
    from(rustFuzzyDir.resolve("target/release")) {
        include("*.so", "*.dylib", "*.dll")
    }
    into(layout.buildDirectory.dir("resources/main/natives/${detectHostPlatform()}"))
    enabled = !skipCargo
}
```

**For distribution:** The pre-compiled native libs should be committed to `src/main/resources/natives/<platform>/` so they are included in the plugin JAR without requiring a Rust toolchain at build time. The `copyNativeLib` Gradle task can remain for local dev (building from source), but distribution requires the pre-compiled binaries to be in-tree.

**Platform naming convention** (matches `NucleoNative.detectPlatform()`):
- `darwin-aarch64` — macOS Apple Silicon
- `darwin-x86_64` — macOS Intel
- `linux-x86_64` — Linux
- `win32-x86_64` — Windows

**Library file naming** (matches `NucleoNative.libName()`):
- macOS: `libfuzzyjni.dylib`
- Linux: `libfuzzyjni.so`
- Windows: `fuzzyjni.dll`

The pre-compiled `libfuzzyjni.dylib` for `darwin-aarch64` already exists at `rust-fuzzy/target/release/libfuzzyjni.dylib` on the developer's machine. It needs to be placed at `src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib`.

### Pattern 4: Status Bar Layout Change

**What:** The current status bar panel (in `QuickOpenPanel.init`):
```kotlin
val statusPanel = JPanel(BorderLayout()).apply {
    add(truncationLabel, BorderLayout.WEST)
    add(countLabel, BorderLayout.EAST)
    add(hintLabel, BorderLayout.CENTER)
}
```

Needs to become a two-row south area: top row = condensed hint bar, bottom row = ranker/root info. Or the hint bar can become the bottom row and the ranker/root info can replace the CENTER of the status panel.

**Decision-compliant approach:** Replace the hint bar content and place the ranker/root info line in CENTER or as a second status row. The ranker name comes from `RankerSelector.active.name` (already available — `RankerBackend` interface has `val name: String`). The root path comes from `currentPath`. File count comes from `candidatePool.getCandidates().size` (after enumeration completes).

**Status bar text construction:**
```kotlin
val rankerName = RankerSelector.active.name
    .replace("NucleoRanker (JNI)", "Nucleo")
    .replace("MinusculeMatcher", "MinusculeMatcher")
    .replace("FallbackRanker", "Fallback")
val rootDisplay = currentPath.replace(System.getProperty("user.home"), "~")
val fileCount = candidatePool.getCandidates().size
val statusText = "Fuzzy: $rankerName · Root: $rootDisplay · ${"%,d".format(fileCount)} files"
```

**Root path clickable:** Wrap the root label in a `MouseAdapter` that opens a directory picker. `FileChooserFactory.getInstance().createPathChooser(...)` is the IntelliJ-native API; `JFileChooser` is simpler but not theme-aware.

### Pattern 5: rg Match Navigation to Line Number

**What:** When a `CONTENT_MATCH` result is selected and Enter is pressed, open the file at the exact line number.

**Pattern (IntelliJ standard):**
```kotlin
// In activateSelected(), when candidate.type == CandidateType.CONTENT_MATCH:
val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath) ?: return
val lineNumber = candidate.id.substringAfterLast(':').toIntOrNull() ?: 1
OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)
```

The `candidate.id` for content matches is already `"content:${match.filePath}:${match.lineNumber}"` (confirmed in `QuickOpenPanel.performContentSearch()`, line 526), so the line number is reliably extractable.

Note: `OpenFileDescriptor` uses 0-based line numbers, so `lineNumber - 1` is required.

**`FileEditorManager.openFile()` already available** in `showOpenWithMenu()` — the same pattern can be used but without line navigation. `OpenFileDescriptor` is the correct choice for line-precise navigation.

### Pattern 6: Speed Dial from Pure Frecency

**What:** The current `speedDialItems` computation (lines 115–123 of `QuickOpenPanel.kt`) filters by `CandidateType.BOOKMARK` first and falls back to `CandidateType.RECENT`. The decision requires pure frecency from `FrecencyStore` — both files and directories, whatever was visited most recently.

**Current (bookmark-priority):**
```kotlin
private val speedDialItems: List<SearchCandidate> = run {
    val bookmarks = candidates.filter { it.type == CandidateType.BOOKMARK }
        .sortedByDescending { it.signals.lastUsedMs }
        .take(6)
    if (bookmarks.isNotEmpty()) bookmarks
    else candidates.filter { it.type == CandidateType.RECENT }
        .sortedByDescending { it.signals.lastUsedMs }
        .take(6)
}
```

**Correct (pure frecency):**
```kotlin
private val speedDialItems: List<SearchCandidate> = run {
    val store = FrecencyStore.getInstance()
    candidates
        .filter { it.type != CandidateType.ACTION }
        .sortedByDescending { store.recencyScore(it.fullPath) }
        .take(QuickOpenSettings.getInstance().state.speedDialCount)
}
```

`FrecencyStore.recencyScore()` uses exponential decay — paths visited more recently score higher regardless of type. `speedDialCount` defaults to 6 from `QuickOpenSettings.State`.

### Pattern 7: Debounce Tuning (Dual-Delay)

**Current:** Single 150ms debounce for all queries (line 462 of `QuickOpenPanel.kt`).

**Required:** 50ms for fuzzy, 300ms for rg content search.

**Implementation:**
```kotlin
private fun scheduleSearch() {
    pendingSearch?.cancel(true)
    val raw = searchField.text.trim()
    resultList.setPaintBusy(true)
    // ... card switching, mode chip update ...
    val delayMs = if (QueryParser.parse(raw).mode == QueryMode.CONTENT_SEARCH) 300L else 50L
    pendingSearch = searchScheduler.schedule({
        val results = performSearch(raw)
        ApplicationManager.getApplication().invokeLater({
            updateList(results, raw)
        }, ModalityState.any())
    }, delayMs, TimeUnit.MILLISECONDS)
}
```

### Pattern 8: rg Extra Flags Allowlist Validation

**Current (no validation, line 77 of `RipgrepContentSearch.kt`):**
```kotlin
if (extra.isNotBlank()) cmd.addAll(extra.split("\\s+".toRegex()))
```

**Required (allowlist validation):**
```kotlin
private val ALLOWED_FLAGS = setOf("--hidden", "--no-ignore", "--follow")
private val ALLOWED_PREFIXES = setOf("--type", "--glob")

private fun validateExtraFlags(flags: String): List<String> {
    return flags.trim().split("\\s+".toRegex()).filter { token ->
        when {
            token.isBlank() -> false
            token in ALLOWED_FLAGS -> true
            ALLOWED_PREFIXES.any { token.startsWith(it) } -> true
            token.startsWith("--exec") -> false  // explicit block
            token.startsWith("--") -> false       // block unknown -- flags
            else -> false
        }
    }
}

// Usage:
val validated = validateExtraFlags(extra)
cmd.addAll(validated)
```

### Pattern 9: Path Segment Highlight in Renderer

**What:** The CONTEXT requires match character highlighting in both the filename AND the path segment. Currently `SearchResultRenderer` only highlights the `displayName` using `scored.matchedRanges` (lines 51–63). The path segment (`parentPath`) is always shown in `GRAYED_ATTRIBUTES` with no highlighting.

**Consideration:** `matchedRanges` from the ranker covers character positions in `displayName` only. Highlighting in `parentPath` would require the ranker to provide path-segment match ranges separately. This is a **discretion area** for implementation — the simplest approach is to use `SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES` for any characters in parentPath that match the query string literally (substring search), rather than fuzzy match positions.

**Anti-Patterns to Avoid**
- Starting a new `CoroutineScope` inside a function that runs on every keystroke — creates unbounded scope growth
- Using `System.load()` directly from an in-JAR native without first extracting to a temp file — this already works correctly in `NucleoNative.tryLoad()`; do not change the extraction pattern
- Making `SearchResultRenderer` do filesystem I/O (e.g., calling `LocalFileSystem.findFileByPath`) on every render call — this is already done and is acceptable since IntelliJ's VFS caches the results, but do not add additional filesystem operations
- Setting `popup.closeOk(null)` in paths that could run before `qoPanel.popup = popup` is executed in `QuickOpenPopup.show()` — the null-safe change eliminates this race

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Line-precise file navigation | Custom editor positioning | `OpenFileDescriptor(project, vf, line - 1, 0).navigate(true)` | IntelliJ standard; handles split editors, focus, history |
| Directory picker for root change | Custom JDialog with JFileChooser | `FileChooserFactory.getInstance().createPathChooser(FileChooserDescriptor(false, true, ...))` | Theme-aware, IntelliJ-consistent |
| Native library extraction from JAR | Custom extraction code | Existing `NucleoNative.tryLoad()` pattern (already correct) | Pattern already handles temp file, deleteOnExit, platform detection |
| Fuzzy matching of path segments | Custom string search | Literal substring match using `String.contains()` for parent path badges | Path segments are full paths, not file names — exact substring is simpler and more accurate |
| Coroutine cancellation | Thread.interrupt() / Process.destroy() in finally | `panelScope.cancel()` in `dispose()` + `process.destroy()` already in `RipgrepContentSearch` finally block | Already handled correctly; just needs scope parenting |

## Common Pitfalls

### Pitfall 1: Nucleo Native Path Mismatch

**What goes wrong:** `NucleoNative.tryLoad()` looks for `/natives/darwin-aarch64/libfuzzyjni.dylib` on the classpath. The `copyNativeLib` Gradle task copies to `layout.buildDirectory.dir("resources/main/natives/darwin-aarch64/")`. At runtime inside the plugin JAR, the file needs to be in `src/main/resources/natives/darwin-aarch64/` (which gets packaged into the JAR during `processResources`). The build dir copy IS also picked up by `processResources` since `build/resources/main` is in the output classpath. So both locations work — but only the `src/main/resources` location will be committed to VCS and usable by other developers without a Rust toolchain.

**Why it happens:** Misunderstanding of how Gradle resource merging works.

**How to avoid:** After running `cargo build --release`, copy the native lib to `src/main/resources/natives/<platform>/` and commit it. The Gradle `copyNativeLib` task is for local development convenience and CI; the committed native is for distribution.

**Warning signs:** `NucleoNative unavailable: Native lib not found: /natives/darwin-aarch64/libfuzzyjni.dylib` in IDE log. `RankerSelector.active.name` returns "MinusculeMatcher" instead of "NucleoRanker (JNI)".

### Pitfall 2: CoroutineScope Leak Memory/Thread Accumulation

**What goes wrong:** Each call to `performContentSearch()` creates a new `CoroutineScope(Dispatchers.IO + Job())`. These scopes are never cancelled. If the user types rapidly, many rg processes are spawned and their scopes accumulate. Only the `latch.await(10, TimeUnit.SECONDS)` prevents the calling thread from blocking — but the launched coroutines continue running.

**Why it happens:** The unparented scope has no lifecycle. Even when the popup closes and `dispose()` runs, these coroutines continue.

**How to avoid:** Use `panelScope` (a scope created in the class body and cancelled in `dispose()`). The `latch`/`CountDownLatch` pattern remains valid for the blocking wait — only the scope needs to change.

**Warning signs:** Thread count increasing over time while Quick Open is open. `rg` processes visible in `ps aux` after Quick Open popup is closed.

### Pitfall 3: lateinit JBPopup Crash on Early Access

**What goes wrong:** `popup` is a `lateinit var`. `QuickOpenPopup.show()` creates the panel, then creates the popup, then sets `qoPanel.popup = popup`. If any code path in `QuickOpenPanel.init` triggers `activateSelected()` or `speedDialPanel.onActivated` before `popup` is set (e.g., a rapid keyboard event or the `initialQuery` path), accessing `popup.closeOk(null)` throws `UninitializedPropertyAccessException`.

**Why it happens:** `lateinit` guarantees type safety but not initialization ordering. The panel's `init` block runs synchronously, but `qoPanel.popup = popup` runs on the next line in `QuickOpenPopup.show()`.

**How to avoid:** Change to `var popup: JBPopup? = null`. The race window between panel creation and popup assignment is eliminated because `null` is a valid state.

**Warning signs:** `UninitializedPropertyAccessException: lateinit property popup has not been initialized` in IDE exception log.

### Pitfall 4: rg Content Match Line Number Offset

**What goes wrong:** `OpenFileDescriptor` uses 0-based line numbers. `rg --json` returns 1-based `line_number`. Using the rg line number directly navigates to the wrong line.

**Why it happens:** Different conventions between rg JSON output and IntelliJ's `OpenFileDescriptor`.

**How to avoid:** Always subtract 1 from the rg `lineNumber` when constructing `OpenFileDescriptor`. The line number is stored in the candidate id as `content:<path>:<lineNumber>` where `lineNumber` is 1-based.

**Warning signs:** Navigation lands one line above the actual match.

### Pitfall 5: rg Flag Injection via Extra Flags

**What goes wrong:** `ripgrepExtraFlags` is a user-controlled string that is split and passed directly to `ProcessBuilder`. A user (or corrupted settings file) could inject `--exec someCommand` which causes rg to execute arbitrary commands on the filesystem.

**Why it happens:** No validation on the settings field before passing to ProcessBuilder.

**How to avoid:** Validate each token against an allowlist before adding to the command. Reject anything not explicitly permitted. See Pattern 8.

**Warning signs:** Unexpected process execution or filesystem changes during Quick Open content search.

### Pitfall 6: Speed Dial Displays Stale frecency Before Enumeration Completes

**What goes wrong:** `speedDialItems` is computed in `QuickOpenPanel`'s `init` block from the `candidates` list passed at construction. This list includes bookmarks and recent paths from navigation history, but NOT the dynamically enumerated `CandidatePool` candidates. Pure frecency from `FrecencyStore` works correctly here since `FrecencyStore.recencyScore()` operates on the path strings and does not depend on the pool.

**Why it's not actually a problem:** The speed dial only shows paths the user has previously visited (which are in `FrecencyStore`). The `candidates` list passed to `QuickOpenPanel` already contains `BOOKMARK` and `RECENT` types sourced from navigation history, so frecency-sorting these is both correct and complete for the speed dial use case.

**How to avoid:** No change needed — just ensure `FrecencyStore.recencyScore()` is used instead of `it.signals.lastUsedMs` directly, since `recencyScore()` applies the configured half-life decay.

## Code Examples

### rg Line Navigation (OpenFileDescriptor)

```kotlin
// In QuickOpenPanel.activateSelected(), add CONTENT_MATCH branch:
// Source: IntelliJ Platform SDK — OpenFileDescriptor is the standard for line navigation
private fun openContentMatchAtLine(candidate: SearchCandidate) {
    val lineNumber = candidate.id.substringAfterLast(':').toIntOrNull() ?: 1
    val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath) ?: return
    NonProjectFileWritingAccessProvider.allowWriting(listOf(vf))
    OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)
    popup?.closeOk(null)
}
```

### Status Bar Text Update

```kotlin
// Called from candidatePool.refreshAsync onUpdate callback (already dispatched to EDT):
private fun updateStatusBar() {
    val rankerLabel = when {
        NucleoNative.isAvailable -> "Nucleo"
        else -> RankerSelector.active.name
    }
    val rootDisplay = currentPath.replace(System.getProperty("user.home"), "~")
    val count = candidatePool.getCandidates().size
    rankerStatusLabel.text = "Fuzzy: $rankerLabel · Root: $rootDisplay · ${"%,d".format(count)} files"
}
```

### Frecency-Only Speed Dial

```kotlin
// Replace speedDialItems computation in QuickOpenPanel:
private val speedDialItems: List<SearchCandidate> = run {
    val store = FrecencyStore.getInstance()
    val count = try { QuickOpenSettings.getInstance().state.speedDialCount } catch (_: Exception) { 6 }
    candidates
        .filter { it.type != CandidateType.ACTION }
        .sortedByDescending { store.recencyScore(it.fullPath) }
        .take(count)
}
```

### Condensed Hint Bar Text

```kotlin
// Replace hintLabel initialization in QuickOpenPanel:
private val hintLabel = JBLabel(
    "↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close"
).apply {
    font = font.deriveFont(10f)
    foreground = java.awt.Color.GRAY
    border = javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)
}
```

### Dual-Debounce

```kotlin
// In scheduleSearch():
val isContentSearch = QueryParser.parse(raw).mode == QueryMode.CONTENT_SEARCH
val delayMs = if (isContentSearch) 300L else 50L
pendingSearch = searchScheduler.schedule({ ... }, delayMs, TimeUnit.MILLISECONDS)
```

## State of the Art

| Old Approach | Current Approach | Status | Impact |
|--------------|------------------|--------|--------|
| Single 150ms debounce | Dual 50ms/300ms debounce | To implement | Snappier fuzzy, fewer rg spawns |
| Bookmark-first speed dial | Pure frecency speed dial | To implement | More useful defaults |
| Unparented CoroutineScope | Panel-lifecycle scope | Bug fix required | Prevents coroutine/process leak |
| `lateinit popup` | `var popup: JBPopup? = null` | Bug fix required | Eliminates rare crash |
| No rg flag validation | Allowlist validation | Security fix required | Prevents command injection |
| Native not bundled in resources/ | Committed to src/main/resources/natives/ | Root cause fix | Makes Nucleo work for all users |

## Open Questions

1. **Cross-platform Nucleo binaries**
   - What we know: `darwin-aarch64` binary exists locally. Linux and Windows binaries are not in the repo.
   - What's unclear: Whether CI/CD can cross-compile Rust (requires target toolchain installation).
   - Recommendation: Commit the `darwin-aarch64` binary immediately (it exists). For `linux-x86_64` and `win32-x86_64`, either set up a CI matrix or document that those platforms fall back to MinusculeMatcher until manually compiled and committed.

2. **Root directory picker UX (Claude's discretion)**
   - What we know: Clicking the root path label should open a picker.
   - What's unclear: Whether to use IntelliJ's `FileChooserFactory` (theme-aware, requires project reference) or plain `JFileChooser` (simpler).
   - Recommendation: Use `FileChooserFactory` — it integrates with IntelliJ's theme and recent directories history.

3. **`?` help key overlay (Claude's discretion)**
   - What we know: User said condense the hint bar, not remove it.
   - What's unclear: Whether a secondary `?` overlay adds enough value given the condensed 5-item hint bar.
   - Recommendation: Skip — condensed hint bar with 5 shortcuts is sufficient. A `?` overlay adds implementation complexity for minimal value.

4. **Path segment match highlighting (Claude's discretion)**
   - What we know: CONTEXT requires highlighted/bolded matches in both filename and path segment.
   - What's unclear: Whether to use fuzzy match positions (requires ranker to return path-segment ranges) or literal substring search.
   - Recommendation: Use case-insensitive literal substring search for path segment highlighting. It is accurate (the path segment is the search context) and requires no ranker changes.

## Validation Architecture

> `workflow.nyquist_validation` is not present in `.planning/config.json` — the config only has `mode`, `depth`, `parallelization`, `commit_docs`, `model_profile`, and `workflow.research/plan_check/verifier`. No `nyquist_validation` key exists. Skipping this section.

## Sources

### Primary (HIGH confidence)

- **Codebase direct read** — `QuickOpenPanel.kt`, `RipgrepContentSearch.kt`, `NucleoNative.kt`, `NucleoRanker.kt`, `RankerSelector.kt`, `FrecencyStore.kt`, `SpeedDialPanel.kt`, `SearchResultRenderer.kt`, `CandidatePool.kt`, `QuickOpenSettings.kt`, `QuickOpenPopup.kt`, `SearchResult.kt`, `Ranker.kt`, `RankerBackend.kt`, `build.gradle.kts` — all findings derived from direct source inspection
- **07-CONTEXT.md** — User decisions locked in during discuss-phase
- **IntelliJ Platform SDK pattern** — `OpenFileDescriptor(project, vf, line - 1, 0).navigate(true)` is the documented standard for line navigation; `FileEditorManager.openFile()` already used in codebase for non-line navigation

### Secondary (MEDIUM confidence)

- `rust-fuzzy/target/release/libfuzzyjni.dylib` confirmed present on developer machine — cross-platform binaries not verified as present
- `rg --json` output format (line_number field) — confirmed from `RipgrepContentSearch.ContentMatch.parseJson()` which already parses `line_number` correctly

### Tertiary (LOW confidence)

- Cross-platform Cargo cross-compilation availability on CI — not verified; flagged as open question

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all APIs verified from existing codebase
- Architecture: HIGH — all patterns derived from reading existing working code
- Pitfalls: HIGH for coroutine/popup/native issues (confirmed by code reading); MEDIUM for rg flag injection (pattern is clear, risk level assessment is judgment)

**Research date:** 2026-02-28
**Valid until:** 2026-04-28 (stable IntelliJ platform APIs, stable Kotlin coroutines patterns)
