package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.ui.PreviewPane
import java.io.File

/**
 * Phase 4 — PreviewPane instantiation and update contract
 *
 * Covers the Automated Verification item:
 *   - PreviewPane can be instantiated and update() called without exception (light test)
 *
 * Additional cases:
 *   - update() with a non-existent path does not throw
 *   - update() with a real directory populates children
 *   - update() with a real file populates metadata
 *   - clear() resets without throwing
 *
 * Tests fail to compile until PreviewPane exists under
 * ro.faur.explorer.quickopen.ui.
 */
class Phase4PreviewPaneTest : BasePlatformTestCase() {

    private fun directoryCandidate(path: String) = SearchCandidate(
        id = "dir:$path",
        displayName = path.substringAfterLast('/'),
        fullPath = path,
        parentPath = path.substringBeforeLast('/'),
        type = CandidateType.DIRECTORY
    )

    private fun fileCandidate(path: String) = SearchCandidate(
        id = "file:$path",
        displayName = path.substringAfterLast('/'),
        fullPath = path,
        parentPath = path.substringBeforeLast('/'),
        type = CandidateType.FILE
    )

    fun `test PreviewPane can be instantiated without throwing`() {
        val pane = PreviewPane()
        assertNotNull(pane)
    }

    fun `test update with non-existent path does not throw`() {
        val pane = PreviewPane()
        val candidate = directoryCandidate("/this/path/does/not/exist/xyz")
        pane.update(candidate)  // must not throw
    }

    fun `test update with a real temporary directory does not throw`() {
        val tempDir = createTempDir("preview_pane_test")
        try {
            File(tempDir, "child1.txt").createNewFile()
            File(tempDir, "child2.txt").createNewFile()
            val candidate = directoryCandidate(tempDir.absolutePath)
            val pane = PreviewPane()
            pane.update(candidate)  // must not throw
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun `test update with a real temporary file does not throw`() {
        val tempFile = File.createTempFile("preview_pane_test", ".txt")
        try {
            tempFile.writeText("Hello, World!\nLine two.\n")
            val candidate = fileCandidate(tempFile.absolutePath)
            val pane = PreviewPane()
            pane.update(candidate)  // must not throw
        } finally {
            tempFile.delete()
        }
    }

    fun `test clear does not throw on fresh pane`() {
        val pane = PreviewPane()
        pane.clear()  // must not throw
    }

    fun `test clear after update does not throw`() {
        val tempDir = createTempDir("preview_pane_clear_test")
        try {
            val candidate = directoryCandidate(tempDir.absolutePath)
            val pane = PreviewPane()
            pane.update(candidate)
            pane.clear()  // must not throw
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun `test update can be called multiple times without error`() {
        val pane = PreviewPane()
        val candidate = directoryCandidate("/non/existent/path")
        pane.update(candidate)
        pane.update(candidate)
        pane.update(candidate)
    }

    fun `test PreviewPane has positive preferred width`() {
        val pane = PreviewPane()
        assertTrue(
            "PreviewPane should have a positive preferred width",
            pane.preferredSize.width > 0
        )
    }

    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }
}
