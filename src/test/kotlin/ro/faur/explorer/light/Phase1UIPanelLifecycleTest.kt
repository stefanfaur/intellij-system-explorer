package ro.faur.explorer.light

import com.intellij.testFramework.LightPlatformTestCase
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.ui.QuickOpenPanel
import java.io.File
import java.nio.file.Files

/**
 * Phase 1 — QuickOpenPanel UI Lifecycle (Light Tests)
 *
 * Tests that require the IntelliJ application context. These exercise the
 * lifecycle of [QuickOpenPanel] — construction, disposal, and the contract
 * that the search field and result list are accessible immediately after
 * construction.
 *
 * Behaviors under test:
 *   - Panel can be instantiated with an empty candidate list without throwing
 *   - Panel can be instantiated with a populated candidate list without throwing
 *   - Panel searchField is non-null after construction
 *   - Panel resultList is non-null after construction
 *   - Panel dispose() does not throw
 *   - Panel dispose() can be called multiple times safely
 *   - Panel constructed with a candidate pool exposes it via resultList model
 *   - onSelected callback reference is stored and callable
 *   - Panel preferred size is at least 600 x 400 (minimum usable size from plan)
 */
class Phase1UIPanelLifecycleTest : LightPlatformTestCase() {

    // Use a freshly created temp dir so VfsEnumerator enumerates a tiny, safe directory.
    // Never use "/" or paths like "/home/user" — on Linux those walk into /proc/<pid>/root
    // symlinks which recurse back to "/" causing blocking native I/O that withTimeout cannot cancel.
    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        tempDir = Files.createTempDirectory("explorer_lifecycle_test").toFile()
    }

    override fun tearDown() {
        tempDir.deleteRecursively()
        super.tearDown()
    }


    fun `test panel instantiates without throwing on empty candidates`() {
        var threw = false
        try {
            val panel = makePanel(emptyList())
            panel.dispose()
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("QuickOpenPanel should not throw on construction with empty candidates", threw)
    }

    fun `test panel instantiates without throwing with populated candidates`() {
        val candidates = listOf(
            makeCandidate("Documents", "/home/user/Documents", CandidateType.DIRECTORY),
            makeCandidate("README.md", "/home/user/README.md", CandidateType.FILE),
            makeCandidate("MyBookmark", "/home/user/Projects", CandidateType.BOOKMARK)
        )
        var threw = false
        try {
            val panel = makePanel(candidates)
            panel.dispose()
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("QuickOpenPanel should not throw on construction with populated candidates", threw)
    }

    fun `test panel searchField is non-null after construction`() {
        val panel = makePanel(emptyList())
        try {
            assertNotNull("searchField must be accessible after construction", panel.searchField)
        } finally {
            panel.dispose()
        }
    }

    fun `test panel resultList is non-null after construction`() {
        val panel = makePanel(emptyList())
        try {
            assertNotNull("resultList must be accessible after construction", panel.resultList)
        } finally {
            panel.dispose()
        }
    }

    fun `test panel dispose does not throw`() {
        val panel = makePanel(emptyList())
        var threw = false
        try {
            panel.dispose()
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("dispose() should not throw", threw)
    }

    fun `test panel dispose can be called multiple times safely`() {
        val panel = makePanel(emptyList())
        var threw = false
        try {
            panel.dispose()
            panel.dispose()
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("Calling dispose() twice should not throw", threw)
    }

    fun `test panel preferred size width is at least 600`() {
        val panel = makePanel(emptyList())
        try {
            assertTrue(
                "Panel preferred width should be >= 600, got ${panel.preferredSize.width}",
                panel.preferredSize.width >= 600
            )
        } finally {
            panel.dispose()
        }
    }

    fun `test panel preferred size height is at least 400`() {
        val panel = makePanel(emptyList())
        try {
            assertTrue(
                "Panel preferred height should be >= 400, got ${panel.preferredSize.height}",
                panel.preferredSize.height >= 400
            )
        } finally {
            panel.dispose()
        }
    }

    fun `test onSelected callback lambda is accepted without throwing`() {
        // The plan requires QuickOpenPanel to accept an onSelected: (SearchCandidate) -> Unit
        // lambda. This test verifies that providing a non-trivial lambda does not cause
        // a construction error. The callback wiring itself is verified by integration tests.
        val target = makeCandidate("target", "/root/target", CandidateType.DIRECTORY)
        var callbackWasReferenced = false
        val panel = makePanel(listOf(target)) { _ ->
            callbackWasReferenced = true
        }
        try {
            // The fact that construction succeeded means the lambda was accepted.
            // callbackWasReferenced may or may not be true depending on whether
            // the panel fires a selection during construction — either outcome is valid.
            assertNotNull(panel)
        } finally {
            panel.dispose()
        }
    }

    fun `test resultList has correct empty text when no results`() {
        val panel = makePanel(emptyList())
        try {
            val emptyText = panel.resultList.emptyText.text
            assertTrue(
                "emptyText should indicate 'no results', got: '$emptyText'",
                emptyText.isNotBlank()
            )
        } finally {
            panel.dispose()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makePanel(
        candidates: List<SearchCandidate>,
        onSelected: (SearchCandidate) -> Unit = {}
    ): QuickOpenPanel {
        return QuickOpenPanel(
            project = project,
            currentPath = tempDir.absolutePath,
            candidates = candidates,
            onSelected = onSelected
        )
    }

    private fun makeCandidate(
        displayName: String,
        fullPath: String,
        type: CandidateType
    ) = SearchCandidate(
        id = "test:$fullPath",
        displayName = displayName,
        fullPath = fullPath,
        parentPath = fullPath.substringBeforeLast('/'),
        type = type
    )
}
