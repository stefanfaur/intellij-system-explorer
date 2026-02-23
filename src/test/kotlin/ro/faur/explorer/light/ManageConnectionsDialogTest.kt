package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.ui.ManageConnectionsDialog

class ManageConnectionsDialogTest : BasePlatformTestCase() {

    fun `test dialog can be constructed without crashing`() {
        val dialog = ManageConnectionsDialog(project)
        assertNotNull(dialog)
        dialog.disposeIfNeeded()
    }

    fun `test dialog is non-modal`() {
        val dialog = ManageConnectionsDialog(project)
        assertFalse("ManageConnectionsDialog should be non-modal", dialog.isModal)
        dialog.disposeIfNeeded()
    }
}
