package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.actions.NavigationActions
import ro.faur.explorer.ui.QuickOpenDialog

class QuickOpenDialogTest : BasePlatformTestCase() {

    fun `test dialog can be created with empty history`() {
        val history = NavigationActions.NavigationHistory()
        val dialog = QuickOpenDialog(project, history)
        assertNotNull(dialog)
    }

    fun `test dialog can be created with populated history`() {
        val history = NavigationActions.NavigationHistory()
        history.push("/usr")
        history.push("/tmp")
        val dialog = QuickOpenDialog(project, history)
        assertNotNull(dialog)
    }

    fun `test getSelectedPath returns text field content`() {
        val history = NavigationActions.NavigationHistory()
        val dialog = QuickOpenDialog(project, history)
        dialog.setPathText("/usr/local")
        assertEquals("/usr/local", dialog.getSelectedPath())
    }

    fun `test getSelectedPath trims whitespace`() {
        val history = NavigationActions.NavigationHistory()
        val dialog = QuickOpenDialog(project, history)
        dialog.setPathText("  /usr/local  ")
        assertEquals("/usr/local", dialog.getSelectedPath())
    }

    fun `test isValidPath returns true for existing directory`() {
        // isValidPath is a static method on companion object
        // /tmp should exist on all test platforms
        assertTrue(QuickOpenDialog.isValidPath("/tmp"))
    }

    fun `test isValidPath returns false for nonexistent path`() {
        assertFalse(QuickOpenDialog.isValidPath("/nonexistent/path/that/does/not/exist"))
    }

    fun `test isValidPath returns false for empty string`() {
        assertFalse(QuickOpenDialog.isValidPath(""))
    }

    fun `test isValidPath returns false for file path not directory`() {
        // /etc/hosts is a file, not a directory
        assertFalse(QuickOpenDialog.isValidPath("/etc/hosts"))
    }

    fun `test getRecentPathsList returns history paths`() {
        val history = NavigationActions.NavigationHistory()
        history.push("/first")
        history.push("/second")
        history.push("/third")

        val dialog = QuickOpenDialog(project, history)
        val recentPaths = dialog.getRecentPathsList()
        assertEquals(listOf("/third", "/second", "/first"), recentPaths)
    }
}
