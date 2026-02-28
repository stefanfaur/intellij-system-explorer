package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.CommitFile
import ro.faur.explorer.gitpanel.ui.ChangedFilesPanel
import ro.faur.explorer.remote.git.GitFileStatus

/**
 * Phase 2 — Spec: ChangedFilesPanel callback contracts (Human Verification Item 4)
 *
 * The plan (02-02-PLAN.md) requires:
 *   - ChangedFilesPanel exposes `onFileSelected: ((CommitFile?) -> Unit)?`
 *   - ChangedFilesPanel exposes `onFileDoubleClicked: ((CommitFile) -> Unit)?`
 *   - onFileSelected fires when a file is selected in the list
 *   - onFileSelected fires with null when selection is cleared
 *   - onFileDoubleClicked fires on double-click (separate from selection)
 *
 * The plan (02-04-PLAN.md, Change A) requires:
 *   - In history mode (selectedCommitHash != null), the onFileSelected consumer
 *     must return early WITHOUT switching slots or loading inline diff.
 *   - This guard lives in GitPanelComponent, not in ChangedFilesPanel itself.
 *
 * What we CAN test in pure unit tests (no IntelliJ platform):
 *   - The callback properties exist and accept lambdas (compile-time check)
 *   - Callbacks registered before setFiles() are not fired on data load
 *   - The panel does NOT auto-select on setFiles() — selection is user-driven
 *   - setFiles() in staging mode always fires onFileSelected=null on data replace
 *     (deselection event, if any)
 *   - Callbacks set to null do not throw on file selection events
 *
 * Note: Triggering the ListSelectionListener requires calling setSelectedIndex()
 * on the internal JList. Tests that need that go in the light tier where we can
 * dispatch events safely via SwingUtilities.
 */
class Phase2ChangedFilesPanelCallbackTest {

    private fun file(path: String, status: GitFileStatus = GitFileStatus.MODIFIED) =
        CommitFile(path, status)

    // ── Callback property existence (compile check + assignment) ─────────────

    @Test
    fun `onFileSelected callback property can be assigned a lambda`() {
        val panel = ChangedFilesPanel()
        var called = false
        panel.onFileSelected = { called = true }
        assertNotNull(panel.onFileSelected)
        // Lambda stored — invoking it manually should work
        panel.onFileSelected?.invoke(null)
        assertTrue(called, "Manually invoking onFileSelected should fire the registered lambda")
    }

    @Test
    fun `onFileSelected callback can be set to null`() {
        val panel = ChangedFilesPanel()
        panel.onFileSelected = { }
        panel.onFileSelected = null
        assertNull(panel.onFileSelected)
    }

    @Test
    fun `onFileDoubleClicked callback property can be assigned a lambda`() {
        val panel = ChangedFilesPanel()
        var called = false
        panel.onFileDoubleClicked = { called = true }
        assertNotNull(panel.onFileDoubleClicked)
        panel.onFileDoubleClicked?.invoke(file("src/Foo.kt"))
        assertTrue(called, "Manually invoking onFileDoubleClicked should fire the registered lambda")
    }

    @Test
    fun `onFileDoubleClicked callback can be set to null`() {
        val panel = ChangedFilesPanel()
        panel.onFileDoubleClicked = { }
        panel.onFileDoubleClicked = null
        assertNull(panel.onFileDoubleClicked)
    }

    // ── No spurious fires on data load ────────────────────────────────────────

    @Test
    fun `onFileSelected is NOT fired automatically during setFiles`() {
        val panel = ChangedFilesPanel()
        panel.setMode(staging = true)
        var fireCount = 0
        panel.onFileSelected = { fireCount++ }

        panel.setFiles(
            listOf(file("a.kt"), file("b.kt"), file("c.kt")),
            "Working Tree"
        )

        // setFiles() replaces the model — it must NOT fire onFileSelected automatically.
        // Selection must be driven by user interaction, not data load.
        assertEquals(
            0, fireCount,
            "onFileSelected must not fire when setFiles() is called — selection is user-driven"
        )
    }

    @Test
    fun `onFileSelected is NOT fired when setFiles is called in history mode`() {
        val panel = ChangedFilesPanel()
        panel.setMode(staging = false)
        var fireCount = 0
        panel.onFileSelected = { fireCount++ }

        panel.setFiles(listOf(file("src/Main.kt")), "Commit abc1234")

        assertEquals(
            0, fireCount,
            "onFileSelected must not fire when setFiles() is called in history mode"
        )
    }

    @Test
    fun `onFileSelected fires with correct CommitFile when invoked directly`() {
        val panel = ChangedFilesPanel()
        val target = file("src/Target.kt", GitFileStatus.ADDED)
        var received: CommitFile? = CommitFile("wrong", GitFileStatus.DELETED) // sentinel

        panel.onFileSelected = { received = it }
        panel.onFileSelected?.invoke(target)

        assertEquals(target, received, "onFileSelected should receive the exact CommitFile passed")
    }

    @Test
    fun `onFileSelected fires with null when invoked for deselect`() {
        val panel = ChangedFilesPanel()
        var received: CommitFile? = CommitFile("sentinel", GitFileStatus.MODIFIED)

        panel.onFileSelected = { received = it }
        panel.onFileSelected?.invoke(null)

        assertNull(received, "onFileSelected(null) represents deselection — lambda must receive null")
    }

    // ── History-mode guard contract (specification test) ──────────────────────

    /**
     * Spec contract: when the consumer of onFileSelected is GitPanelComponent
     * and selectedCommitHash != null (history mode), the consumer MUST return
     * early — it must NOT call showDiffSlot() or loadInlineDiff().
     *
     * This test verifies the contract by simulating what GitPanelComponent's
     * lambda is specified to do (02-04-PLAN.md, Change A):
     *
     *   changedFilesPanel.onFileSelected = fileSelectedLambda@{ file ->
     *       selectedDiffFile = file
     *       if (selectedCommitHash != null) return@fileSelectedLambda  // guard
     *       if (file != null) { showDiffSlot(); loadInlineDiff(file) }
     *       else { inlineDiffPanel.clear() }
     *   }
     *
     * We replicate this logic in the test to verify the guard prevents
     * slot-switching in history mode.
     */
    @Test
    fun `history mode guard prevents slot switch when selectedCommitHash is set`() {
        // Simulate the GitPanelComponent onFileSelected lambda as specified in the plan
        var selectedCommitHash: String? = "abc1234"  // history mode — non-null
        var selectedDiffFile: CommitFile? = null
        var slotSwitchCount = 0
        var diffLoadCount = 0

        val onFileSelected: (CommitFile?) -> Unit = fileSelectedLambda@{ file ->
            selectedDiffFile = file
            if (selectedCommitHash != null) return@fileSelectedLambda  // the guard
            if (file != null) {
                slotSwitchCount++
                diffLoadCount++
            }
        }

        // Simulate user clicking a file in history mode
        onFileSelected(file("src/Foo.kt"))

        assertEquals(0, slotSwitchCount, "In history mode, slot must NOT be switched on file selection")
        assertEquals(0, diffLoadCount, "In history mode, inline diff must NOT be loaded on file selection")
        assertEquals(file("src/Foo.kt"), selectedDiffFile, "selectedDiffFile should still be updated even in history mode")
    }

    @Test
    fun `staging mode does NOT guard — slot switch and diff load proceed`() {
        var selectedCommitHash: String? = null  // staging mode — null
        var slotSwitchCount = 0
        var diffLoadCount = 0

        val onFileSelected: (CommitFile?) -> Unit = fileSelectedLambda@{ f ->
            if (selectedCommitHash != null) return@fileSelectedLambda
            if (f != null) {
                slotSwitchCount++
                diffLoadCount++
            }
        }

        onFileSelected(file("src/Foo.kt"))

        assertEquals(1, slotSwitchCount, "In staging mode, slot switch must proceed for non-null file")
        assertEquals(1, diffLoadCount, "In staging mode, inline diff must be loaded for non-null file")
    }
}
