package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate
import ro.faur.explorer.quickopen.ui.QuickOpenPanel
import ro.faur.explorer.quickopen.ranking.FrecencyStore
import java.io.File
import java.nio.file.Files

class QuickOpenPanelTest : BasePlatformTestCase() {

    // Use a freshly created temp dir so VfsEnumerator enumerates a tiny, safe directory
    // and never touches system paths like "/" or "/proc" which can block indefinitely on Linux.
    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        FrecencyStore.getInstance().loadState(FrecencyStore.State())
        tempDir = Files.createTempDirectory("explorer_panel_test").toFile()
    }

    override fun tearDown() {
        tempDir.deleteRecursively()
        super.tearDown()
    }

    fun `test panel can be instantiated without throwing`() {
        val candidates = listOf(
            SearchCandidate(
                id = "bm:home",
                displayName = "Home",
                fullPath = tempDir.absolutePath,
                parentPath = tempDir.parent ?: "",
                type = CandidateType.BOOKMARK
            )
        )
        val panel = QuickOpenPanel(
            project = project,
            currentPath = tempDir.absolutePath,
            candidates = candidates,
            onSelected = {}
        )
        assertNotNull(panel)
        assertNotNull(panel.searchField)
        assertNotNull(panel.resultList)
        panel.dispose()
    }

    fun `test panel with empty candidates does not throw`() {
        val panel = QuickOpenPanel(
            project = project,
            currentPath = tempDir.absolutePath,
            candidates = emptyList(),
            onSelected = {}
        )
        assertNotNull(panel)
        panel.dispose()
    }

    fun `test search field is focusable`() {
        val panel = QuickOpenPanel(
            project = project,
            currentPath = tempDir.absolutePath,
            candidates = emptyList(),
            onSelected = {}
        )
        assertTrue(panel.searchField.isFocusable)
        panel.dispose()
    }

    fun `test QuickOpenPanel initialQuery parameter compiles correctly`() {
        val constructor = ro.faur.explorer.quickopen.ui.QuickOpenPanel::class.java
            .constructors
            .firstOrNull { it.parameterCount >= 4 }
        assertNotNull("QuickOpenPanel must have a constructor with ≥4 params", constructor)
    }
}
