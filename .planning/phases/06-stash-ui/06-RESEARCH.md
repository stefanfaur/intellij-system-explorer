# Phase 6: Stash UI - Research

**Researched:** 2026-03-01
**Domain:** IntelliJ Platform Swing UI — Git stash CRUD panel with CardLayout, DialogWrapper, diff preview
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Stash list placement**
- New dedicated card in the existing CardLayout — same pattern as CommitLogPanel/ChangedFilesPanel
- Navigation: toolbar button "Stash List" toggles between commit log view and stash card
- Layout when on stash card: stash list in left slot, stash diff preview (vs parent commit) in right slot
- After apply or drop: auto-return to commit log view
- No count badge on the toolbar button — icon only

**Apply vs Pop backend**
- Keep existing `stashPop(index)` (used by auto-stash-on-checkout flow)
- Add two new methods to `GitBackend`: `stashApply(index)` (apply, keep stash) and `stashDrop(index)` (delete only)
- UI exposes three actions: **Apply** (keep stash), **Pop** (apply + drop), **Drop** (delete only)
- Errors shown as notification balloons — consistent with Pull/Push error handling
- After successful apply: stash entry stays selected (so user can easily drop it next)

**Stash creation dialog**
- Follow `CreateBranchDialog` pattern: `DialogWrapper` + `FormBuilder`
- Fields: optional message (`JBTextField`) with placeholder showing git default (e.g. "WIP on main: abc1234 last commit msg"), include-untracked `JCheckBox`
- OK button enabled immediately (empty message = git default)
- Include-untracked: unchecked by default; state is NOT persisted across sessions
- After successful stash creation: auto-navigate to stash card so user sees the new entry

**Stash list item display & actions**
- Row format: message only — no `stash@{N}:` prefix (cleaner; index is an implementation detail)
- Secondary toolbar scoped to stash card (only rendered/visible when stash card is active): Apply, Pop, Drop buttons enabled when a row is selected
- Right-click context menu on a row mirrors toolbar exactly: Apply, Pop, Drop
- Right slot shows diff of stash against its parent commit (what changes are stashed)

### Claude's Discretion
- Exact icon choices for Apply/Pop/Drop toolbar buttons
- Stash diff panel implementation details (reuse InlineDiffPanel or similar)
- Empty state UI when stash list is empty

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| STASH-01 | User can create a stash with an optional message from the Git panel toolbar | `CreateStashDialog` (DialogWrapper + FormBuilder pattern), "Stash" toolbar action wired to `backend.stash(message, includeUntracked)` |
| STASH-02 | Stash creation includes untracked files (--include-untracked surfaced in dialog) | `JCheckBox` field in dialog; `backend.stash()` already accepts `includeUntracked` param and passes `--include-untracked` to git |
| STASH-03 | User can view a list of all stash entries in the Git panel | `StashListPanel` — new card in GitPanelComponent's main split; `backend.stashList()` already exists |
| STASH-04 | User can apply a selected stash entry | New `stashApply(index)` on GitBackend (`git stash apply stash@{N}`); Apply and Pop toolbar/context actions on StashListPanel |
| STASH-05 | User can drop (delete) a selected stash entry (with confirmation) | New `stashDrop(index)` on GitBackend (`git stash drop stash@{N}`); Drop action with `JOptionPane.showConfirmDialog` |

</phase_requirements>

## Summary

Phase 6 extends the existing GitPanelComponent with a stash management view. The codebase already has all the git primitives in place: `stash()`, `stashList()`, and `stashPop()` are implemented in both `LocalGitBackend` and `RemoteGitBackend`. The UI work amounts to: (1) a new `CreateStashDialog` following the `CreateBranchDialog` pattern, (2) a new `StashListPanel` card that slots into the existing CardLayout architecture in `GitPanelComponent`, (3) two new backend methods `stashApply` and `stashDrop`, and (4) toolbar/context-menu wiring.

The stash diff preview in the right slot reuses `InlineDiffPanel` directly. Stash diff content is fetched by comparing `stash@{N}^` (parent commit, "before" state) with the stash's tracked-file tree (`git show stash@{N}:path`). This is the same pattern as history-mode diff in `buildDiffRequest()`.

The most important architectural constraint is that the new stash card lives at the **main split level** (replacing `commitLogPanel` in the top slot and hijacking the right slot for diff), not at the bottom-split level — this matches the decision that "stash list in left slot, stash diff preview in right slot" should be the full panel layout when the stash card is active.

**Primary recommendation:** Implement as three sequential plans — (1) backend methods, (2) StashListPanel + CreateStashDialog, (3) GitPanelComponent wiring + CardLayout integration.

## Standard Stack

### Core (already in project — no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.intellij.openapi.ui.DialogWrapper` | IJ 2024.3 | Modal dialogs | Provides OK/Cancel with validation, focus management |
| `com.intellij.util.ui.FormBuilder` | IJ 2024.3 | Form layout in dialogs | Consistent label+field vertical layout |
| `com.intellij.ui.components.JBList` | IJ 2024.3 | Scrollable list component | IntelliJ-styled, integrates with platform theme |
| `com.intellij.ui.ColoredListCellRenderer` | IJ 2024.3 | List row renderer | Supports multiple text spans with attributes |
| `com.intellij.openapi.actionSystem.AnAction` (inline) | IJ 2024.3 | Toolbar and context actions | Existing pattern in GitPanelComponent |
| `javax.swing.JOptionPane` | JDK | Confirmation dialogs | Used for Delete Branch — same pattern for Drop |
| `com.intellij.notification.Notifications` | IJ 2024.3 | Error balloons | Existing `notifyError()` helper in GitPanelComponent |
| `com.intellij.openapi.progress.Task.Backgroundable` | IJ 2024.3 | Background git ops | Applied/drop may block; prevents EDT freeze |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.intellij.ui.components.JBTextField` | IJ 2024.3 | Message input in dialog | Already used in CreateBranchDialog |
| `com.intellij.ui.components.JBScrollPane` | IJ 2024.3 | Scroll wrapper for list | Platform-themed scroll pane |
| `DefaultListModel<StashEntry>` | JDK | Mutable list backing | Minimal, rebuildable on stash refresh |
| `ApplicationManager.getApplication().executeOnPooledThread` | IJ 2024.3 | Off-EDT git calls | Lighter than Task.Backgroundable for read ops |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Inline AnAction objects in toolbar | Registered plugin XML actions | XML actions require IDs and are global; inline scoped to panel — correct choice |
| `JOptionPane.showConfirmDialog` | `Messages.showYesNoDialog` | Both work; JOptionPane is what Delete Branch uses — keep consistent |
| Separate `StashDiffPanel` class | Reuse `InlineDiffPanel` directly | InlineDiffPanel is already generic; creating a subclass adds file count with no benefit |

## Architecture Patterns

### Recommended Project Structure (additions only)
```
src/main/kotlin/ro/faur/explorer/gitpanel/
├── GitBackend.kt                     # Add stashApply(), stashDrop()
├── LocalGitBackend.kt                # Implement stashApply(), stashDrop()
├── RemoteGitBackend.kt               # Implement stashApply(), stashDrop()
└── ui/
    ├── CreateStashDialog.kt          # NEW: DialogWrapper + FormBuilder
    ├── StashListPanel.kt             # NEW: JBList-based panel, secondary toolbar, right-click menu
    └── GitPanelComponent.kt          # Add "Stash" and "Stash List" toolbar actions, stash card wiring
```

### Pattern 1: GitBackend Extension Methods

Both `stashApply` and `stashDrop` follow the exact same one-liner pattern as `stashPop`:

```kotlin
// In LocalGitBackend.kt
override fun stashApply(index: Int): GitCommandResult =
    executor.executeBlocking(repoPath, args = arrayOf("stash", "apply", "stash@{$index}"))

override fun stashDrop(index: Int): GitCommandResult =
    executor.executeBlocking(repoPath, args = arrayOf("stash", "drop", "stash@{$index}"))

// In RemoteGitBackend.kt — identical pattern
```

### Pattern 2: CreateStashDialog (follows CreateBranchDialog exactly)

```kotlin
class CreateStashDialog(
    project: Project,
    private val defaultMessageHint: String  // e.g. "WIP on main: abc1234 last commit"
) : DialogWrapper(project, true) {

    private val messageField = JBTextField(40).apply {
        emptyText.text = defaultMessageHint
    }
    private val includeUntrackedBox = JCheckBox("Include untracked files").apply {
        isSelected = false  // unchecked default; not persisted
    }

    init {
        title = "Create Stash"
        isOKActionEnabled = true  // empty message = git default — always enabled
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Message (optional):", messageField)
            .addComponent(includeUntrackedBox)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = messageField

    fun getMessage(): String? = messageField.text.trim().ifBlank { null }
    fun isIncludeUntracked(): Boolean = includeUntrackedBox.isSelected
}
```

**Key difference from CreateBranchDialog:** OK button is always enabled (empty message is valid — git uses its default). No validator needed.

### Pattern 3: StashListPanel Structure

```kotlin
class StashListPanel : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<StashEntry>()
    private val list = JBList(listModel)

    // Secondary toolbar — Apply, Pop, Drop
    private val applyAction  = object : AnAction("Apply", ...) { ... }
    private val popAction    = object : AnAction("Pop", ...) { ... }
    private val dropAction   = object : AnAction("Drop", ...) { ... }

    var onApply: ((StashEntry) -> Unit)? = null
    var onPop:   ((StashEntry) -> Unit)? = null
    var onDrop:  ((StashEntry) -> Unit)? = null
    var onStashSelected: ((StashEntry?) -> Unit)? = null

    init {
        // Build secondary toolbar (horizontal, scoped to this panel)
        val group = DefaultActionGroup()
        group.add(applyAction); group.add(popAction); group.add(dropAction)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("StashPanel.Toolbar", group, true)
        toolbar.targetComponent = list

        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)

        // Right-click context menu mirrors toolbar
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                list.selectedIndex = idx
                buildContextMenu().show(list, e.x, e.y)
            }
        })

        // Selection drives right slot diff and action enablement
        list.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            onStashSelected?.invoke(list.selectedValue)
        }

        list.emptyText.text = "No stashes"
    }

    fun setEntries(entries: List<StashEntry>) {
        listModel.clear()
        entries.forEach { listModel.addElement(it) }
    }

    private fun buildContextMenu(): JPopupMenu { ... }  // Apply, Pop, Drop items
}
```

### Pattern 4: CardLayout Integration in GitPanelComponent

The main split currently has:
- Top: `commitLogPanel`
- Bottom-left: `changedFilesPanel`
- Bottom-right: `rightSlot` (CardLayout with CARD_DETAILS / CARD_DIFF)

Adding the stash card means adding a **new top-level card** that replaces the entire split when active. This requires a second CardLayout wrapping the `mainSplit`:

```kotlin
companion object {
    private const val CARD_DETAILS  = "details"
    private const val CARD_DIFF     = "diff"
    private const val CARD_MAIN     = "main"    // NEW: commit log + staging view
    private const val CARD_STASH    = "stash"   // NEW: stash list + stash diff
}

private val mainViewLayout = CardLayout()
private val mainView = JPanel(mainViewLayout)

// In buildCenter():
// 1. Build existing mainSplit as before
// 2. Build stashView (StashListPanel LEFT + InlineDiffPanel RIGHT in a JBSplitter)
// 3. Add both to mainView under CARD_MAIN and CARD_STASH
// 4. Add mainView to the BorderLayout.CENTER of GitPanelComponent

private val stashListPanel = StashListPanel()
private val stashDiffPanel = InlineDiffPanel(project, this)  // reuse same class
```

**Toggle behavior:**
```kotlin
private var onStashCard = false

private fun showStashCard() {
    onStashCard = true
    mainViewLayout.show(mainView, CARD_STASH)
    loadStashList()
}

private fun showMainCard() {
    onStashCard = false
    mainViewLayout.show(mainView, CARD_MAIN)
}
```

### Pattern 5: Stash Diff Content

To show what a stash contains, diff stash's tracked-file tree against its parent:

```kotlin
// git show stash@{N}:path         — stash version of file
// git show stash@{N}^:path        — parent commit version of file (before stash was created)

fun getStashFileContent(index: Int, path: String): ByteArray? {
    val result = executor.executeBlocking(repoPath, args = arrayOf("show", "stash@{$index}:$path"))
    return if (result.isSuccess) result.stdout.toByteArray(Charsets.UTF_8) else null
}

fun getStashParentContent(index: Int, path: String): ByteArray? {
    val result = executor.executeBlocking(repoPath, args = arrayOf("show", "stash@{$index}^:$path"))
    return if (result.isSuccess) result.stdout.toByteArray(Charsets.UTF_8) else null
}
```

For listing files in a stash to populate the stash diff file list, use:
```bash
git diff --name-status stash@{N}^ stash@{N}
```

This is the same format as `diff-tree --name-status` already parsed by `parseDiffTreeLine()`.

**Alternative approach (simpler for this phase):** Show stash diff as a single aggregated diff using `buildDiffRequest()` with a sentinel "stash" file, or skip per-file selection and show the full patch text. However, per the CONTEXT.md decision ("right slot shows diff of stash against its parent commit"), the InlineDiffPanel approach with per-file selection is preferred, so a `StashDiffFileList` + `InlineDiffPanel` split within the stash card makes sense.

**Simplest viable layout for stash card:**
```
┌─────────────────────────────────────────────────────────┐
│  StashListPanel (list + secondary toolbar)  [LEFT SLOT] │
│  InlineDiffPanel                           [RIGHT SLOT]  │
└─────────────────────────────────────────────────────────┘
```
The right slot shows the diff immediately when a stash is selected (aggregate patch view using `git stash show -p stash@{N}` rendered in InlineDiffPanel). This avoids needing a two-level split (stash list | file list | diff) in phase 6.

**Recommended approach for stash diff:** Use `git stash show --stat stash@{N}` to list files, and on stash selection automatically show the first changed file's diff (or show the full patch). This is Claude's discretion territory per CONTEXT.md.

### Anti-Patterns to Avoid

- **Wrapping stash actions in `Task.Backgroundable` unnecessarily:** Apply/Drop are fast local operations (`executeOnPooledThread` is sufficient). Reserve `Task.Backgroundable` for operations that need IDE status bar progress (like Pull).
- **Forgetting the snapshotKey stale-result guard:** All background callbacks in GitPanelComponent use `System.identityHashCode(selectedBackend) != snapshotKey` to discard stale results. Stash operations must do the same.
- **EDT violations:** `stashList()`, `stashApply()`, `stashDrop()` must never be called on the EDT. Always dispatch to `executeOnPooledThread`.
- **Using `stashPop` for the "Apply" action:** `stashPop` removes the entry (equivalent to apply+drop). The new `stashApply` (keep) and `stashDrop` (delete only) are separate operations.
- **Reloading the full panel after apply:** After `stashApply`, only reload the stash list; do not navigate away (entry stays selected). After `stashDrop` or `stashPop`, reload stash list and auto-navigate back to commit log view.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Modal dialog with OK/Cancel | Custom JDialog | `DialogWrapper` | Provides focus, ESC key, platform look-and-feel, keyboard shortcuts |
| Form layout | Manual GridBagLayout | `FormBuilder.createFormBuilder()` | Consistent label alignment, handles LTR/RTL |
| Diff viewer panel | Custom text pane | `InlineDiffPanel` (wraps `DiffManager.createRequestPanel`) | Handles syntax highlighting, scrolling, disposable lifecycle |
| List component | `JList` bare | `JBList` | Platform theming, accessibility, speed search support |
| Error reporting | Custom status bar | `Notifications.Bus.notify` via `notifyError()` | Consistent with existing Pull/Push error UX |
| Confirmation prompt | Custom dialog | `JOptionPane.showConfirmDialog` | Matches Delete Branch pattern; simpler than DialogWrapper for yes/no |

**Key insight:** The diff infrastructure (`InlineDiffPanel`, `buildDiffRequest`, `DiffContentFactory`) is already present and generic. Stash diff reuses it with different content sources — no new diff machinery needed.

## Common Pitfalls

### Pitfall 1: Stash Index Staleness
**What goes wrong:** User applies/drops stash index N, but the UI list still shows old indices. When they try to act on another entry, the index is wrong because git renumbers stashes.
**Why it happens:** Stash indices (0, 1, 2...) are contiguous and shift after any drop/pop. `StashEntry.index` stored at list-load time becomes incorrect after the list changes.
**How to avoid:** Always reload `stashList()` after any apply/pop/drop and rebuild the list model from the fresh response. Never act on a cached index without first refreshing.
**Warning signs:** User drops stash 0, then tries to drop "stash 1" and gets "stash@{1} does not exist" error.

### Pitfall 2: ActionUpdateThread Mismatch
**What goes wrong:** Apply/Pop/Drop actions read `list.selectedValue` (EDT state) from `getActionUpdateThread() = BGT`, causing threading assertion failures.
**Why it happens:** `ActionUpdateThread.BGT` means `update()` runs on a background thread, but Swing component state must only be accessed on EDT.
**How to avoid:** Use `ActionUpdateThread.EDT` for the secondary toolbar actions that read `list.selectedValue` or `list.selectedIndex`. This matches the `Create Branch` / `Delete Branch` precedent in GitPanelComponent.

### Pitfall 3: Double CardLayout Navigation
**What goes wrong:** Clicking "Stash List" while already on stash card triggers `loadStashList()` again, causing a flicker or double network call.
**Why it happens:** The toolbar action toggles unconditionally.
**How to avoid:** Check `onStashCard` flag before switching: if already on stash card, `showStashCard()` is a no-op.

### Pitfall 4: notifyError Group ID
**What goes wrong:** `notifyError()` in GitPanelComponent uses `"SystemExplorer"` as the notification group ID. New stash error notifications must use the same group ID or they won't appear.
**Why it happens:** The notification group must be registered in `plugin.xml`. Using an unregistered ID silently drops the notification in some IJ versions.
**How to avoid:** Use the existing `"SystemExplorer"` group ID in all new `Notifications.Bus.notify` calls.

### Pitfall 5: `--include-untracked` on Remote Repos
**What goes wrong:** `git stash push --include-untracked` may behave differently on remote (SSH) repos where the working tree is not what we expect (the backend reads from the index for diffs).
**Why it happens:** `RemoteGitBackend` uses `git show :path` (index proxy) for HEAD content; untracked files are not in the index. The stash itself is fine, but the diff preview of untracked changes is a known limitation.
**How to avoid:** The stash operation itself (`git stash push --include-untracked`) works fine on remote. The limitation is only in diff preview of untracked content. Document this as a known limitation; don't add special casing.

### Pitfall 6: Empty Stash List on "Stash" Creation
**What goes wrong:** User creates a stash when there are no changes in the working tree. `git stash push` exits with `No local changes to save` (exit code 1).
**Why it happens:** Git returns non-zero when there's nothing to stash.
**How to avoid:** Check `result.isSuccess` after `backend.stash()`. If failed, show the error via `notifyError()`. The user will see "No local changes to save" as the balloon message.

## Code Examples

### Example 1: Backend Method Signatures to Add to GitBackend Interface

```kotlin
// Source: existing stashPop pattern in GitBackend.kt
fun stashApply(index: Int): ro.faur.explorer.gitpanel.exec.GitCommandResult
fun stashDrop(index: Int): ro.faur.explorer.gitpanel.exec.GitCommandResult
```

### Example 2: LocalGitBackend Implementation Pattern

```kotlin
// Source: stashPop pattern in LocalGitBackend.kt (line 136-138)
override fun stashApply(index: Int): GitCommandResult =
    executor.executeBlocking(repoPath, args = arrayOf("stash", "apply", "stash@{$index}"))

override fun stashDrop(index: Int): GitCommandResult =
    executor.executeBlocking(repoPath, args = arrayOf("stash", "drop", "stash@{$index}"))
```

### Example 3: Stash Action Invocation Pattern in GitPanelComponent

```kotlin
// Source: doCheckoutBranch pattern in GitPanelComponent.kt
private fun doStashApply(entry: StashEntry) {
    val backend = selectedBackend ?: return
    val snapshotKey = System.identityHashCode(backend)
    ApplicationManager.getApplication().executeOnPooledThread {
        val result = backend.stashApply(entry.index)
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
            if (result.isSuccess) loadStashList()  // reload, stay on stash card, keep selection
            else notifyError("Stash apply failed: ${result.stderr.takeLast(200)}")
        }
    }
}

private fun doStashDrop(entry: StashEntry) {
    val confirmed = JOptionPane.showConfirmDialog(
        this,
        "Delete stash '${entry.message}'?",
        "Confirm Drop",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE
    ) == JOptionPane.OK_OPTION
    if (!confirmed) return
    val backend = selectedBackend ?: return
    val snapshotKey = System.identityHashCode(backend)
    ApplicationManager.getApplication().executeOnPooledThread {
        val result = backend.stashDrop(entry.index)
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
            if (result.isSuccess) showMainCard()  // auto-return to commit log
            else notifyError("Stash drop failed: ${result.stderr.takeLast(200)}")
        }
    }
}
```

### Example 4: Stash Diff Content Retrieval

```kotlin
// Git commands for stash diff:
// List files changed: git diff --name-status stash@{N}^ stash@{N}
// Before version:     git show stash@{N}^:path/to/file
// After (stash) ver:  git show stash@{N}:path/to/file
//
// Parsed by existing parseDiffTreeLine() — no new parser needed.
//
// For stash diff, build SimpleDiffRequest using buildDiffRequest() with:
//   leftLabel  = "Before stash"
//   rightLabel = "Stashed changes"
```

### Example 5: Default Stash Message Hint

```kotlin
// To provide the placeholder hint in CreateStashDialog, fetch current branch + HEAD hash/subject:
// Branch: backend.getCurrentBranch()
// HEAD summary: git log -1 --format="%h %s"
// Combine: "WIP on $branch: $hash $subject"
// Pass as defaultMessageHint to CreateStashDialog constructor
// This is constructed off-EDT before showing dialog
```

### Example 6: Stash List Cell Renderer

```kotlin
// StashEntry.message already contains git's default message format
// ("WIP on branch: hash subject" or custom message)
// Strip the "stash@{N}:" prefix (already done by parseStashLine in LocalGitBackend.kt)
// Display message only — use ColoredListCellRenderer for consistency

object : ColoredListCellRenderer<StashEntry>() {
    override fun customizeCellRenderer(
        list: JList<out StashEntry>,
        value: StashEntry,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        append(value.message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `git stash save` | `git stash push` | Git 2.13 | `save` deprecated; project already uses `push` correctly |
| `git stash apply stash@{N}` (apply only) | Same — no change | N/A | `apply` keeps stash; `drop` deletes; `pop` = apply+drop |
| Manual diff via `git stash show -p` | `DiffManager.createRequestPanel` with `SimpleDiffRequest` | Project convention | InlineDiffPanel handles lifecycle, disposal, syntax highlighting |

**Deprecated/outdated:**
- `git stash save`: Replaced by `git stash push` in git 2.13. Project already uses `push` in both backends.
- Raw `JList` with `DefaultListCellRenderer`: Use `JBList` + `ColoredListCellRenderer` (IJ convention in this project).

## Open Questions

1. **Stash diff right slot — per-file or aggregate?**
   - What we know: CONTEXT.md says "right slot shows diff of stash against its parent commit". InlineDiffPanel shows a single file diff. If we want per-file selection within the stash card, we need a file list sub-panel.
   - What's unclear: Does "right slot" mean a single consolidated patch view, or per-file selection as in the staging view?
   - Recommendation: Implement per-file selection for consistency with the staging view. The stash card layout becomes: [stash list | [stash file list | stash file diff]]. This is Claude's discretion per CONTEXT.md. If complexity is too high, fall back to showing only the first changed file's diff on stash selection.

2. **`notifyError` title for stash operations**
   - What we know: Existing `notifyError()` uses "Branch Error" as the notification title.
   - What's unclear: Should stash errors use a different title ("Stash Error") or the same helper?
   - Recommendation: Extract `notifyError(title, msg)` overload or simply inline `Notifications.Bus.notify` with "Stash Error" title for clarity.

## Sources

### Primary (HIGH confidence)
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` — interface with existing stash methods, StashEntry data class
- `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` — stash(), stashList(), stashPop() implementations, parseStashLine()
- `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` — same methods in remote backend
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` — CardLayout pattern, snapshotKey guard, notifyError(), doDeleteBranch() confirm pattern
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/CreateBranchDialog.kt` — dialog template to follow
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/InlineDiffPanel.kt` — reusable diff slot component
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/ChangedFilesPanel.kt` — list panel pattern reference

### Secondary (MEDIUM confidence)
- Git documentation for `git stash apply`, `git stash drop`, `git stash show` — commands are stable since git 2.13; behavior well-understood
- STATE.md accumulated context — confirms patterns for threading, error reporting, snapshotKey guard

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use in the project
- Architecture: HIGH — patterns are directly observable in existing code
- Pitfalls: HIGH — derived from observable patterns and known git behavior
- Backend git commands: HIGH — `git stash apply/drop` are stable, well-documented git commands

**Research date:** 2026-03-01
**Valid until:** 2026-09-01 (stable platform; git stash API is not changing)
