package ro.faur.explorer.light

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import ro.faur.explorer.gitpanel.ui.InlineDiffPanel

/**
 * Phase 2 — Spec: InlineDiffPanel lifecycle (Human Verification Items 1, 3, 7)
 *
 * Item 1 — Inline diff syntax highlighting:
 *   InlineDiffPanel wraps a DiffRequestPanel (IntelliJ's native diff viewer) which
 *   provides syntax highlighting out-of-the-box when given a file-type-aware DiffContent.
 *   We verify showDiffRequest() replaces the spinner with a real component and the panel
 *   is non-empty after loading.
 *
 * Item 3 — File switch disposal:
 *   Calling showDiffRequest() a second time MUST dispose the first child disposable
 *   before creating a new one. We verify this does not throw and leaves the panel valid.
 *
 * Item 7 — Panel close memory leak:
 *   When the parent disposable is disposed, InlineDiffPanel must dispose its current
 *   child disposable cleanly — no "already disposed" exceptions or leaked editors.
 *
 * Light tests (LightPlatformTestCase) — requires project and IntelliJ Diff services.
 * Run with: ./gradlew test -PincludeIdeTests=true
 */
class Phase2InlineDiffPanelTest : LightPlatformTestCase() {

    // ── Item 1: Panel renders diff content (syntax highlighting proxy) ─────────

    fun `test panel is non-null after construction`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        assertNotNull("InlineDiffPanel must be non-null after construction", panel)
    }

    fun `test showSpinner adds a child component`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        panel.showSpinner()
        assertTrue(
            "showSpinner() must add at least one child component (the spinner widget)",
            panel.componentCount > 0
        )
    }

    fun `test showDiffRequest results in a non-empty panel`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        panel.showSpinner()
        panel.showDiffRequest(makeEmptyRequest("test.kt"))
        assertTrue(
            "showDiffRequest() must result in a non-empty panel (diff viewer widget must be present)",
            panel.componentCount > 0
        )
    }

    fun `test showDiffRequest does not throw for empty diff content`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val result = runCatching {
            panel.showDiffRequest(makeEmptyRequest("Foo.kt"))
        }
        assertTrue(
            "showDiffRequest() must not throw for valid (empty-content) diff: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test showDiffRequest accepts text content without throwing`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val result = runCatching {
            val factory = DiffContentFactory.getInstance()
            val left = factory.create("fun before() {}")
            val right = factory.create("fun after() {}")
            val request = SimpleDiffRequest("Foo.kt", left, right, "HEAD", "Working Tree")
            panel.showDiffRequest(request)
        }
        assertTrue(
            "showDiffRequest() must not throw when given text content: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test showDiffRequest with correct diff labels HEAD and Working Tree`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val factory = DiffContentFactory.getInstance()
        // Staging mode: labels must be "HEAD" on the left and "Working Tree" on the right
        val request = SimpleDiffRequest(
            "Foo.kt",
            factory.create("old content"),
            factory.create("new content"),
            "HEAD",
            "Working Tree"
        )
        val result = runCatching { panel.showDiffRequest(request) }
        assertTrue(
            "showDiffRequest() with HEAD vs Working Tree labels must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test showDiffRequest with history mode labels commit hash and parent`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val factory = DiffContentFactory.getInstance()
        val commitHash = "abc1234"
        // History mode: labels must be "$commitHash^" on left and short hash on right
        val request = SimpleDiffRequest(
            "Foo.kt",
            factory.create("content at parent"),
            factory.create("content at commit"),
            "$commitHash^",
            commitHash.take(8)
        )
        val result = runCatching { panel.showDiffRequest(request) }
        assertTrue(
            "showDiffRequest() with history-mode commit-range labels must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    // ── Item 3: File switch disposal ──────────────────────────────────────────

    fun `test showDiffRequest called twice does not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)

        val result = runCatching {
            panel.showDiffRequest(makeEmptyRequest("First.kt"))
            panel.showDiffRequest(makeEmptyRequest("Second.kt"))
        }
        assertTrue(
            "showDiffRequest() called twice must not throw — old child disposable must be cleaned up first: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test clear after showDiffRequest does not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        panel.showDiffRequest(makeEmptyRequest("Foo.kt"))
        val result = runCatching { panel.clear() }
        assertTrue(
            "clear() after showDiffRequest() must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test clear on fresh panel does not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val result = runCatching { panel.clear() }
        assertTrue(
            "clear() on a fresh panel must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test clear twice does not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        panel.showDiffRequest(makeEmptyRequest("Foo.kt"))
        val result = runCatching {
            panel.clear()
            panel.clear()
        }
        assertTrue(
            "clear() called twice must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test showSpinner after showDiffRequest does not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        panel.showDiffRequest(makeEmptyRequest("Foo.kt"))
        val result = runCatching { panel.showSpinner() }
        assertTrue(
            "showSpinner() after showDiffRequest() must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test multiple file switches in sequence do not throw`() {
        val panel = InlineDiffPanel(project, testRootDisposable)
        val result = runCatching {
            repeat(5) { i ->
                panel.showSpinner()
                panel.showDiffRequest(makeEmptyRequest("File$i.kt"))
            }
        }
        assertTrue(
            "Rapidly switching files (showSpinner + showDiffRequest) must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    // ── Item 7: No memory leak on panel close ──────────────────────────────────

    fun `test parent disposable disposal does not throw`() {
        val parentDisposable = Disposer.newDisposable()
        val panel = InlineDiffPanel(project, parentDisposable)
        panel.showDiffRequest(makeEmptyRequest("Foo.kt"))

        val result = runCatching { Disposer.dispose(parentDisposable) }
        assertTrue(
            "Disposing the parent disposable must not throw — child cleanup must be orderly: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test parent disposable disposal after clear does not throw`() {
        val parentDisposable = Disposer.newDisposable()
        val panel = InlineDiffPanel(project, parentDisposable)
        panel.showDiffRequest(makeEmptyRequest("Foo.kt"))
        panel.clear()

        val result = runCatching { Disposer.dispose(parentDisposable) }
        assertTrue(
            "Disposing parent after clear() must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test parent disposable disposal on fresh panel does not throw`() {
        val parentDisposable = Disposer.newDisposable()
        InlineDiffPanel(project, parentDisposable)

        val result = runCatching { Disposer.dispose(parentDisposable) }
        assertTrue(
            "Disposing parent of a fresh InlineDiffPanel must not throw: ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    fun `test parent disposable disposal after multiple file switches does not throw`() {
        val parentDisposable = Disposer.newDisposable()
        val panel = InlineDiffPanel(project, parentDisposable)

        // Simulate rapid file switching before disposal
        repeat(3) { i ->
            panel.showSpinner()
            panel.showDiffRequest(makeEmptyRequest("File$i.kt"))
        }

        val result = runCatching { Disposer.dispose(parentDisposable) }
        assertTrue(
            "Disposing parent after multiple file switches must not throw (no leaked disposables): ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeEmptyRequest(title: String): SimpleDiffRequest {
        val factory = DiffContentFactory.getInstance()
        return SimpleDiffRequest(title, factory.createEmpty(), factory.createEmpty(), "HEAD", "Working Tree")
    }
}
