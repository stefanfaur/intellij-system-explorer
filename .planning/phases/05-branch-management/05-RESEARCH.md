# Phase 5: Branch Management - Research

**Researched:** 2026-03-01
**Domain:** IntelliJ Platform UI (JBPopup, AnAction toolbar), Git CLI integration (existing LocalGitBackend/RemoteGitBackend), branch name validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- Branch list is a **popup triggered by clicking the existing `branchLabel` in the toolbar** (no new always-visible panel section)
- Popup is a quick-switcher: clicking a branch checks it out — no other actions in the popup
- Create and Delete are separate **toolbar buttons** — not in the popup
- Toolbar button is the only entry point for Create Branch
- After successful checkout: full `reloadData()` — branch label, commit log, and working tree all refresh
- No partial refresh — always reload everything
- When checkout is attempted with uncommitted changes, show a dialog with three options:
  - **Stash & Switch** — auto-stash with message `"Auto-stash before checkout to <target-branch>"`, then checkout
  - **Discard & Switch** — hard-reset working tree, then checkout (requires explicit confirmation within the dialog or strong wording)
  - **Cancel** — abort, do nothing
- No user-editable stash message — auto-generated only
- Simple dialog: single text input for branch name + OK/Cancel
- **Client-side validation**: invalid chars (spaces, `..`, `~`, `^`, `:`, `?`, `*`, `[`, `\`) and reserved names rejected inline; OK button disabled until name is valid
- Always branches from current HEAD (no commit selector)
- **Auto-checkout** the new branch immediately after creation
- Requires selection of a branch in the branch popup (or a toolbar button on the selected branch)
- Standard confirmation dialog for normal case
- **Detect unmerged commits**: if the branch has commits not merged into the current branch, show a **stronger warning dialog** explaining commits would be lost, with a `Force Delete` button and Cancel — no silent data loss
- Force-delete uses `git branch -D`; normal delete uses `git branch -d`

### Claude's Discretion

- Exact popup/list component choice (JBPopup, JBList, etc.)
- Icon choices for branch toolbar buttons
- Exact wording of confirmation dialogs beyond the key behaviors above
- Whether to show branch count in the toolbar label

### Deferred Ideas (OUT OF SCOPE)

- Remote branch tracking / push -u — future phase
- Branch rename — could be a backlog item
- Visual graph of branch divergence — separate scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BRANCH-01 | User can view a list of all local branches in the Git panel, with the current branch highlighted | `listBranches()` already returns `List<BranchInfo>` with `isCurrent`; JBPopupFactory/PopupChooserBuilder provides list UI with custom renderer |
| BRANCH-02 | User can checkout an existing branch from the branch list | `checkoutBranch(name)` already implemented; dirty-tree check (`getWorkingTreeStatus().isNotEmpty()`) needed before calling it |
| BRANCH-03 | User can create a new branch from the current HEAD via a name-input dialog | `createBranch(name)` already implemented (`git checkout -b`); name validation logic needed; dialog via JOptionPane or Messages.showInputDialog |
| BRANCH-04 | User can delete a local branch (with confirmation) from the branch list | `deleteBranch(name, force)` already implemented; unmerged detection via `git branch --no-merged`; two-phase confirmation dialog |
| BRANCH-05 | Checking out a branch with a dirty working tree prompts the user to stash, hard-reset, or cancel (no silent failure) | `getWorkingTreeStatus()` checks dirtiness; `stash(message, includeUntracked)` already implemented; reset via `git reset --hard HEAD`; three-option dialog needed |
</phase_requirements>

---

## Summary

Phase 5 adds branch management UI to the Git panel. The backend layer is already complete — `GitBackend` already declares and both backends implement `listBranches()`, `checkoutBranch()`, `createBranch()`, `deleteBranch()`, and `stash()`. The entire phase is a UI wiring exercise: connecting these existing APIs to toolbar buttons, a popup quick-switcher, validation dialogs, and the dirty-tree safety flow.

The most novel work is: (1) wiring a mouse click on `branchLabel` to a `JBPopupFactory`-based list popup showing branches; (2) implementing the three-option dirty-tree checkout dialog with Stash/Discard/Cancel; (3) detecting unmerged commits before delete using `git branch --no-merged`; and (4) client-side branch name validation with OK button enable/disable.

The established project patterns (pooled thread → invokeLater, stale-result guard with `System.identityHashCode`, `JOptionPane` for confirmations, `AnAction` subclasses in `buildToolbar()`, `Notifications.Bus.notify` for errors) apply directly to every new operation. No new dependencies are needed.

**Primary recommendation:** Wire all new operations through `executeOnPooledThread` + `invokeLater` with the existing stale-result guard. Use `JBPopupFactory.getInstance().createPopupChooserBuilder()` for the branch list popup, `JOptionPane` for simple confirmations, and a custom `JDialog` or `Messages.showInputDialog` for the create-branch input with validation.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| IntelliJ Platform SDK | 2025.1 (build 251) | `JBPopupFactory`, `AnAction`, `JBLabel`, `AllIcons` | Already in project; all UI is IntelliJ platform widgets |
| `javax.swing.JOptionPane` | JDK 21 | Confirmation and input dialogs | Already used in `GitPanelComponent` for push confirmation; zero new imports |
| `com.intellij.openapi.ui.Messages` | Platform | IntelliJ-native input dialogs (alternative to JOptionPane) | Provides `showInputDialog` with built-in OK/Cancel; integrates with IDE look-and-feel |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `JBPopupFactory` | Platform | Branch list popup (quick-switcher style) | `createPopupChooserBuilder(list)` for the branch list triggered by `branchLabel` click |
| `AllIcons` | Platform | Toolbar button icons | `AllIcons.Vcs.Branch` for the branch list button; `AllIcons.General.Add` / `AllIcons.General.Remove` for create/delete |
| `Messages` (com.intellij.openapi.ui) | Platform | `showOkCancelDialog` with custom panel | For dirty-tree three-option dialog if custom button labels are needed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `JBPopupFactory.createPopupChooserBuilder` | `JBPopupFactory.createListPopup` with `ListPopupStep` | `PopupChooserBuilder` requires less boilerplate for simple item lists; `ListPopupStep` gives more control over icons per item |
| `JOptionPane.showInputDialog` | `Messages.showInputDialog` | `Messages` integrates with IDE theme; `JOptionPane` is simpler but looks more Swing-native. Either works; project already uses `JOptionPane`. |
| Custom `JDialog` for dirty-tree | `Messages.showDialog` with custom button array | `Messages.showDialog` returns the button index; simpler than a full custom dialog for three-option dialogs |

---

## Architecture Patterns

### Recommended Project Structure

No new source files needed beyond one or two small helpers. The plan should add logic to:

```
src/main/kotlin/ro/faur/explorer/gitpanel/ui/
    GitPanelComponent.kt      ← all new branch UI wires in here
```

If branch name validation logic grows, it can go in:
```
src/main/kotlin/ro/faur/explorer/gitpanel/
    BranchNameValidator.kt    ← pure function, testable without IDE
```

### Pattern 1: Branch List Popup via JBPopupFactory

**What:** Mouse click on `branchLabel` opens a `JBList`-backed popup. Each item is a `BranchInfo`. Clicking an item triggers checkout (with dirty-tree check first). The current branch is visually distinguished.

**When to use:** Any quick-switcher over a short dynamic list.

**How to attach to branchLabel:**
```kotlin
// In buildToolbar() or init:
branchLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
    override fun mouseClicked(e: java.awt.event.MouseEvent) {
        showBranchPopup(branchLabel)
    }
})

private fun showBranchPopup(anchor: JComponent) {
    val backend = selectedBackend ?: return
    val branches = backend.listBranches()          // already off EDT in practice — load async
    val popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(branches)
        .setTitle("Switch Branch")
        .setItemChosenCallback { branch ->
            if (!branch.isCurrent) doCheckoutBranch(branch.name)
        }
        .setRenderer(BranchListRenderer())         // custom renderer highlights current branch
        .createPopup()
    popup.showUnderneathOf(anchor)
}
```

**Note:** Branch list loading should run on a pooled thread; the popup is created and shown on EDT after the result arrives. This matches the `reloadData()` pattern.

**Confidence:** MEDIUM — `createPopupChooserBuilder` API verified in IJ 2024.x SDK; `showUnderneathOf(anchor)` is the correct method for toolbar anchors (HIGH confidence from project experience with similar popups).

### Pattern 2: Dirty-Tree Dialog with Three Options

**What:** Before `checkoutBranch()`, call `getWorkingTreeStatus()`. If non-empty, show a three-option dialog.

**How IntelliJ's `Messages.showDialog` works:**
```kotlin
// Messages.showDialog returns the index of the button clicked, or -1 for dialog close.
val result = Messages.showDialog(
    project,
    "You have uncommitted changes. What would you like to do?",
    "Dirty Working Tree",
    arrayOf("Stash & Switch", "Discard & Switch", "Cancel"),
    2,       // defaultOptionIndex = Cancel
    Messages.getWarningIcon()
)
when (result) {
    0 -> doStashAndCheckout(targetBranch)
    1 -> doDiscardAndCheckout(targetBranch)
    else -> return  // Cancel or dialog closed
}
```

**Confidence:** HIGH — `Messages.showDialog` is a stable platform API. The button index return pattern is documented.

### Pattern 3: Create Branch Dialog with Validation

**What:** A toolbar button opens an input dialog. The OK button stays disabled until the branch name passes validation. Branch name validation rejects: spaces, `..`, `~`, `^`, `:`, `?`, `*`, `[`, `\`, and names starting or ending with `/` or `.`.

**Implementation approach — custom dialog (preferred for enable/disable OK):**
```kotlin
// Using DialogWrapper for OK button control
class CreateBranchDialog(project: Project) : DialogWrapper(project, true) {
    private val nameField = JBTextField()
    private val errorLabel = JBLabel().apply { foreground = JBColor.RED }

    init {
        title = "Create New Branch"
        init()
        nameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { validateName() }
        })
    }

    override fun createCenterPanel(): JComponent { /* layout nameField + errorLabel */ }

    private fun validateName() {
        val name = nameField.text.trim()
        val error = BranchNameValidator.validate(name)
        errorLabel.text = error ?: ""
        isOKActionEnabled = error == null && name.isNotEmpty()
    }

    fun getBranchName(): String = nameField.text.trim()
}
```

**Alternative:** `Messages.showInputDialog` — simpler but no inline validation or OK button control.

**Recommendation:** Use `DialogWrapper` for the create-branch dialog to get `isOKActionEnabled` control. Use `JOptionPane` or `Messages.showOkCancelDialog` for the simpler delete confirmation.

**Confidence:** HIGH — `DialogWrapper` is the standard IntelliJ dialog base class. `isOKActionEnabled` is documented and used across IntelliJ plugins.

### Pattern 4: Detect Unmerged Commits Before Delete

**What:** Before calling `deleteBranch(name, false)`, check whether the branch has commits not merged into the current branch. Git CLI: `git branch --no-merged HEAD <branchName>`. If output is non-empty, the branch has unmerged commits.

**GitBackend extension needed:** Add `hasBranchUnmergedCommits(name: String): Boolean` to `GitBackend` interface, or implement detection inline in the UI handler by running a raw command through the executor.

**Simplest implementation (inline, LocalGitBackend pattern):**
```kotlin
// Detect unmerged commits before delete (runs on pooled thread):
private fun hasBranchUnmergedCommits(backend: GitBackend, name: String): Boolean {
    // git branch --no-merged HEAD <name> returns non-empty if branch has unmerged commits
    // This is already the git-native check used by git branch -d
    // If deleteBranch(name, force=false) fails with exit code != 0, that's also a reliable signal
    // because git refuses to delete branches with unmerged commits
    return false // see detailed note in pitfalls section
}
```

**Simpler alternative:** Attempt `deleteBranch(name, force=false)`. If it fails (exit code != 0 and stderr contains "not fully merged"), show the stronger warning with Force Delete option. If the user chooses Force Delete, call `deleteBranch(name, force=true)`. This avoids a separate git command and leverages the behavior already implemented.

**Confidence (attempt-and-detect pattern):** HIGH — `git branch -d` already refuses and returns non-zero with a clear stderr message when there are unmerged commits. This is the standard approach.

### Pattern 5: Hard Reset for Discard & Switch

**What:** `git reset --hard HEAD` discards all working tree changes, then `checkoutBranch(targetBranch)` proceeds.

**GitBackend extension needed:** `resetHard(): GitCommandResult` — not currently in the interface. Add to `GitBackend` interface and implement in `LocalGitBackend` and `RemoteGitBackend`.

```kotlin
// LocalGitBackend:
override fun resetHard(): GitCommandResult {
    return executor.executeBlocking(repoPath, args = arrayOf("reset", "--hard", "HEAD"))
}
```

**Confidence:** HIGH — `git reset --hard HEAD` is standard. Needs interface addition.

### Anti-Patterns to Avoid

- **Loading branch list on EDT before showing popup:** Branch list must load on a pooled thread. Never call `backend.listBranches()` on the EDT — it blocks the UI thread.
- **Showing dirty-tree dialog before checking status on pooled thread:** Status check runs on pooled thread; dialog shown on EDT after result. Do not call `getWorkingTreeStatus()` synchronously on EDT.
- **Using `git branch -D` without confirmation for unmerged branches:** Always show the stronger warning before force-delete. The locked decision requires explicit user acknowledgment.
- **Mixing JBPopup creation with git I/O:** Create the popup from a pre-fetched branch list on EDT. Do not make git calls inside popup item callbacks.
- **Not applying stale-result guard after async operations:** All invokeLater blocks must check `System.identityHashCode(selectedBackend) == snapshotKey` before applying results, matching the existing pattern.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Branch name validation regex | Custom validator from scratch | Implement based on git ref-name rules (well-documented); `BranchNameValidator` is a pure function testable in unit tests | Git's rules are specific: not just "no spaces" — see pitfalls section |
| Popup list UI | Custom `JWindow` or popup-like panel | `JBPopupFactory.getInstance().createPopupChooserBuilder()` | Handles keyboard navigation, focus management, auto-dismissal, Esc key, and screen edge clipping automatically |
| Dialog with OK enable/disable | Custom `JDialog` with manual button management | `DialogWrapper` (com.intellij.openapi.ui.DialogWrapper) | `isOKActionEnabled` setter automatically enables/disables the OK button; handles ESC/Enter key bindings |
| Three-option confirmation | Custom `JDialog` | `Messages.showDialog(project, text, title, buttons, defaultIndex, icon)` | Returns button index; IDE-native look-and-feel; no manual layout |

**Key insight:** The IntelliJ platform provides first-class popup and dialog primitives. Custom Swing `JDialog` and `JWindow` solutions fight the platform's focus management and look-and-feel.

---

## Common Pitfalls

### Pitfall 1: Branch Name Validation is More Than "No Spaces"

**What goes wrong:** Implementing only space-rejection, then users can create branches named `..invalid`, `branch~1`, `branch:name`, or `@{upstream}` — git rejects these at the CLI level and the plugin shows a confusing error.

**Why it happens:** The full git ref-name ruleset is not obvious. From `git check-ref-format` documentation:
- Cannot contain: space, `~`, `^`, `:`, `?`, `*`, `[`, `\`, `..` (consecutive dots), `@{`
- Cannot start or end with `/` or `.`
- Cannot contain `//`
- Cannot end with `.lock`
- Cannot be `@` alone
- Cannot contain ASCII control characters

**How to avoid:** Implement `BranchNameValidator.validate(name): String?` that checks all these rules and returns a human-readable error message. Test with JUnit5 unit tests (no IntelliJ platform dependency needed).

**Warning signs:** If validation only checks `name.contains(" ")`, it's incomplete.

### Pitfall 2: Loading Branch List on EDT

**What goes wrong:** Calling `backend.listBranches()` directly in the `mouseClicked` handler blocks the EDT, freezing the IDE for the duration of the git command.

**Why it happens:** `listBranches()` calls `executor.executeBlocking()` which runs a subprocess synchronously.

**How to avoid:** In `mouseClicked`, immediately show a loading state (or just show the popup with whatever was cached), then load branches on `executeOnPooledThread` and populate/refresh the popup from `invokeLater`.

**Simpler approach:** Cache `listBranches()` result in a field (updated during `reloadData()`), and use the cached value for the popup. The popup shows the same branch list as the toolbar label — always up-to-date after any reload.

**Warning signs:** Any call to `backend.*()` directly in an EDT event handler.

### Pitfall 3: Stash Does Not Include Untracked Files by Default

**What goes wrong:** Stash & Switch calls `stash(message, includeUntracked=false)`. New untracked files remain in the working tree, checkout may conflict or partially succeed.

**Why it happens:** `git stash push` by default only stashes tracked modified files. Untracked files are left in place.

**How to avoid:** The locked decision uses auto-stash before checkout. Call `stash(message, includeUntracked=true)` — this matches the `stash()` signature already in `GitBackend`. The `LocalGitBackend.stash()` implementation already passes `--include-untracked` when `includeUntracked=true`.

**Confidence:** HIGH — verified in `LocalGitBackend.stash()` implementation.

### Pitfall 4: `git branch -d` Failure is the Reliable Unmerged-Branch Signal

**What goes wrong:** Trying to pre-check `git branch --no-merged HEAD <name>` and then calling `deleteBranch(force=false)` results in two git invocations and a race condition (branch might be merged between the check and the delete).

**How to avoid:** Attempt `deleteBranch(name, force=false)`. If `!result.isSuccess` and `result.stderr` contains "not fully merged", show the stronger Force Delete warning dialog. Only then call `deleteBranch(name, force=true)`. This is atomic and matches git's own behavior — `git branch -d` is the canonical check.

**Warning signs:** Making a `git branch --no-merged` call before every delete attempt.

### Pitfall 5: Delete Confirmation Must Gate on a Cached "Selected Branch"

**What goes wrong:** The Delete button is a toolbar button. If no branch is selected (i.e., the user hasn't opened the popup and clicked a branch), the delete action has no target. Calling `deleteBranch(currentBranch)` would delete the current branch — git refuses this, but showing an unhelpful error is poor UX.

**How to avoid:** Track a `selectedBranchForDelete: String?` field in `GitPanelComponent`. This field is set when the user clicks a branch in the popup (without checkout occurring — but the locked decision says popup click = checkout).

**Revision of this concern:** Since the popup is a quick-switcher (click = checkout), and delete is a separate toolbar button, the delete button needs a separate mechanism for "which branch to delete." The CONTEXT.md says "Requires selection of a branch in the branch popup." This means: after the user opens and dismisses the popup (or interacts with it), a selected branch context exists.

**Recommended approach:** Add a `deletableBranch: String?` field that tracks the last-highlighted (not necessarily checked-out) branch in the popup. Alternatively, show a second popup when Delete button is clicked: "Select branch to delete" — same branch list, no checkout on click, instead shows confirmation.

**Warning signs:** Delete button operating on the current branch, or having no visual indication of which branch will be deleted.

---

## Code Examples

Verified patterns from existing codebase and IntelliJ Platform:

### Branch Popup Wiring (based on existing patterns)
```kotlin
// In buildToolbar() after building branchLabel:
branchLabel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
branchLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
    override fun mouseClicked(e: java.awt.event.MouseEvent) {
        val branches = cachedBranches  // loaded during reloadData()
        if (branches.isEmpty()) return
        showBranchPopup(branchLabel, branches)
    }
})
```

### Dirty-Tree Checkout Flow (pooled thread pattern)
```kotlin
private fun doCheckoutBranch(targetBranch: String) {
    val backend = selectedBackend ?: return
    val snapshotKey = System.identityHashCode(backend)
    ApplicationManager.getApplication().executeOnPooledThread {
        val status = backend.getWorkingTreeStatus()
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
            if (status.isNotEmpty()) {
                showDirtyTreeDialog(backend, targetBranch, snapshotKey)
            } else {
                performCheckout(backend, targetBranch, snapshotKey)
            }
        }
    }
}
```

### Attempt-and-Detect Delete Pattern
```kotlin
private fun doDeleteBranch(branchName: String) {
    val backend = selectedBackend ?: return
    val snapshotKey = System.identityHashCode(backend)
    // First confirmation
    val confirmed = JOptionPane.showConfirmDialog(
        this, "Delete branch '$branchName'?", "Confirm Delete",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
    ) == JOptionPane.OK_OPTION
    if (!confirmed) return

    ApplicationManager.getApplication().executeOnPooledThread {
        val result = backend.deleteBranch(branchName, force = false)
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
            if (!result.isSuccess && result.stderr.contains("not fully merged")) {
                // Show stronger warning
                val forceConfirmed = JOptionPane.showConfirmDialog(
                    this,
                    "Branch '$branchName' has commits not merged into the current branch.\n" +
                    "Force deleting will permanently lose these commits.\n\nForce delete?",
                    "Unmerged Branch — Force Delete?",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.ERROR_MESSAGE
                ) == JOptionPane.OK_OPTION
                if (!forceConfirmed) return@invokeLater
                ApplicationManager.getApplication().executeOnPooledThread {
                    backend.deleteBranch(branchName, force = true)
                    ApplicationManager.getApplication().invokeLater {
                        if (!disposed) reloadData()
                    }
                }
            } else if (result.isSuccess) {
                reloadData()
            } else {
                Notifications.Bus.notify(Notification(
                    "SystemExplorer", "Delete Failed",
                    result.stderr.takeLast(300).ifBlank { "Unknown error" },
                    NotificationType.ERROR
                ), project)
            }
        }
    }
}
```

### Branch Name Validation (pure function, no IntelliJ dependency)
```kotlin
object BranchNameValidator {
    private val INVALID_CHARS = Regex("""[\s~^:?*\[\\]""")
    private val DOUBLE_DOT = Regex("""\.\.""")

    fun validate(name: String): String? {
        if (name.isBlank()) return "Branch name cannot be empty"
        if (name.startsWith(".") || name.endsWith(".")) return "Cannot start or end with '.'"
        if (name.startsWith("/") || name.endsWith("/")) return "Cannot start or end with '/'"
        if (name.contains("//")) return "Cannot contain '//'"
        if (DOUBLE_DOT.containsMatchIn(name)) return "Cannot contain '..'"
        if (INVALID_CHARS.containsMatchIn(name)) return "Contains invalid character"
        if (name.contains("@{")) return "Cannot contain '@{'"
        if (name == "@") return "Cannot be '@' alone"
        if (name.endsWith(".lock")) return "Cannot end with '.lock'"
        return null  // valid
    }
}
```

### `resetHard` Addition to GitBackend Interface
```kotlin
// In GitBackend interface:
fun resetHard(): GitCommandResult

// In LocalGitBackend:
override fun resetHard(): GitCommandResult =
    executor.executeBlocking(repoPath, args = arrayOf("reset", "--hard", "HEAD"))

// In RemoteGitBackend: same pattern as other command methods
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ListPopupStep` + `createListPopup` | `createPopupChooserBuilder(list)` | IJ 2020+ | Simpler API for common cases; less boilerplate |
| `com.intellij.openapi.ui.DialogWrapper` requires `createSouthPanel()` override | `isOKActionEnabled` setter | Stable across all versions | Clean way to enable/disable OK button |

**Deprecated/outdated:**
- `TreeSpeedSearch` constructor (already replaced in Phase 4 — `TREE-04`)
- `createListPopup` with anonymous `ListPopupStep` — still works, but `createPopupChooserBuilder` is preferred for simple item lists

---

## Open Questions

1. **Delete button UX: How does the user select a branch to delete?**
   - What we know: The popup is a quick-switcher (click = checkout). Delete is a separate toolbar button. CONTEXT.md says "Requires selection of a branch in the branch popup."
   - What's unclear: Does "selection" mean the user hovers/navigates in the popup without clicking? Or does the delete button open its own separate list popup?
   - Recommendation: Show a separate "Select branch to delete" popup when the Delete button is clicked. This is unambiguous and does not require keyboard-only popup selection state. The planner should confirm this interpretation.

2. **`resetHard` not in GitBackend interface — needs adding**
   - What we know: `git reset --hard HEAD` is needed for "Discard & Switch". The interface currently lacks this method.
   - What's unclear: Whether to add it to the interface or implement it as an inline command in the UI handler using the existing executor.
   - Recommendation: Add to the interface for consistency and testability. Both LocalGitBackend and RemoteGitBackend need the implementation.

3. **Branch list caching strategy**
   - What we know: `reloadData()` already loads branch+log+status on a pooled thread. Branch list can be stored in a field during reload.
   - What's unclear: Whether to re-fetch branches fresh when the popup is opened, or use the cached value.
   - Recommendation: Cache the `List<BranchInfo>` in `GitPanelComponent` (updated in `reloadData()`). Re-fetch in the popup only if the last reload was more than N seconds ago (or just always use cached — simpler, and the panel has a Refresh button).

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection — `GitBackend.kt`, `LocalGitBackend.kt`, `GitPanelComponent.kt` — all existing APIs and patterns verified
- Codebase inspection — `build.gradle.kts`, `gradle.properties` — platform version IC 2025.1 (build 251), JDK 21 confirmed
- `git check-ref-format` documentation — branch name validation rules (stable across git versions)
- Existing `JOptionPane` usage in `GitPanelComponent.doPush()` and `doCommit()` — confirmation dialog pattern confirmed in codebase

### Secondary (MEDIUM confidence)
- IntelliJ Platform SDK patterns for `JBPopupFactory.createPopupChooserBuilder` — based on IntelliJ platform conventions and consistent with other popup usages in the platform (not directly verified via Context7 for this specific version, but API is stable)
- `DialogWrapper` + `isOKActionEnabled` pattern — widely used in IntelliJ plugin ecosystem, stable API

### Tertiary (LOW confidence)
- None — all critical claims are HIGH or MEDIUM confidence.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in the project; no new dependencies needed
- Architecture: HIGH — all backend APIs exist; UI wiring follows established patterns in `GitPanelComponent.kt`
- Pitfalls: HIGH — identified from actual codebase behavior and git CLI behavior; validated against existing implementations
- Branch name validation rules: HIGH — git ref-format rules are stable and well-documented

**Research date:** 2026-03-01
**Valid until:** 2026-09-01 (stable platform APIs; git CLI behavior does not change)
